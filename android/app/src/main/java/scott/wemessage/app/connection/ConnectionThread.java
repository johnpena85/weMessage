package scott.wemessage.app.connection;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v4.content.LocalBroadcastManager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Type;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import scott.wemessage.R;
import scott.wemessage.app.AppLogger;
import scott.wemessage.app.database.DatabaseManager;
import scott.wemessage.app.database.MessageDatabase;
import scott.wemessage.app.database.objects.Account;
import scott.wemessage.app.security.AndroidBase64Wrapper;
import scott.wemessage.app.security.CryptoType;
import scott.wemessage.app.security.DecryptionTask;
import scott.wemessage.app.security.EncryptionTask;
import scott.wemessage.app.security.KeyTextPair;
import scott.wemessage.app.weMessage;
import scott.wemessage.commons.json.connection.ClientMessage;
import scott.wemessage.commons.json.connection.InitConnect;
import scott.wemessage.commons.json.connection.ServerMessage;
import scott.wemessage.commons.json.message.security.JSONEncryptedText;
import scott.wemessage.commons.types.DeviceType;
import scott.wemessage.commons.types.DisconnectReason;
import scott.wemessage.commons.utils.ByteArrayAdapter;

public class ConnectionThread extends Thread {

    private final String TAG = ConnectionService.TAG;
    private final Object serviceLock = new Object();
    private final Object socketLock = new Object();
    private final Object inputStreamLock = new Object();
    private final Object outputStreamLock = new Object();

    private AtomicBoolean isRunning = new AtomicBoolean(false);
    private AtomicBoolean hasTriedAuthenticating = new AtomicBoolean(false);
    private AtomicBoolean isConnected = new AtomicBoolean(false);
    private ConcurrentHashMap<String, ClientMessage>clientMessagesMap = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, ServerMessage>serverMessagesMap = new ConcurrentHashMap<>();

    private ConnectionService service;
    private Socket connectionSocket;
    private ObjectInputStream inputStream;
    private ObjectOutputStream outputStream;

    private final String ipAddress;
    private final int port;
    private String emailPlainText;
    private String passwordPlainText, passwordHashedText;

    protected ConnectionThread(ConnectionService service, String ipAddress, int port, String emailPlainText, String password, boolean alreadyHashed){
        this.service = service;
        this.ipAddress = ipAddress;
        this.port = port;
        this.emailPlainText = emailPlainText;

        if (alreadyHashed){
            this.passwordHashedText = password;
        }else {
            this.passwordPlainText = password;
        }
    }

    public AtomicBoolean isRunning(){
        return isRunning;
    }

    public AtomicBoolean isConnected(){
        return isConnected;
    }

    public ConnectionService getParentService(){
        synchronized (serviceLock){
            return service;
        }
    }

    public Socket getConnectionSocket(){
        synchronized (socketLock){
            return connectionSocket;
        }
    }

    public ObjectInputStream getInputStream(){
        synchronized (inputStreamLock){
            return inputStream;
        }
    }

    public ObjectOutputStream getOutputStream(){
        synchronized (outputStreamLock){
            return outputStream;
        }
    }

    public ServerMessage getIncomingMessage(String prefix, Object incomingStream ){
        String data = ((String) incomingStream).split(prefix)[1];
        ServerMessage serverMessage = new GsonBuilder().registerTypeHierarchyAdapter(byte[].class, new ByteArrayAdapter(new AndroidBase64Wrapper())).create().fromJson(data, ServerMessage.class);

        serverMessagesMap.put(serverMessage.getMessageUuid(), serverMessage);
        return serverMessage;
    }

    public void sendOutgoingMessage(String prefix, Object outgoingData, Class<?> dataClass) throws IOException {
        Type type = TypeToken.get(dataClass).getType();
        String outgoingDataJson = new GsonBuilder().registerTypeHierarchyAdapter(byte[].class, new ByteArrayAdapter(new AndroidBase64Wrapper())).create().toJson(outgoingData, type);
        ClientMessage clientMessage = new ClientMessage(UUID.randomUUID().toString(), outgoingDataJson);
        String outgoingJson = new Gson().toJson(clientMessage);

        getOutputStream().writeObject(prefix + outgoingJson);
        getOutputStream().flush();

        clientMessagesMap.put(clientMessage.getMessageUuid(), clientMessage);
    }

    public void run(){
        isRunning.set(true);
        ByteArrayAdapter byteArrayAdapter = new ByteArrayAdapter(new AndroidBase64Wrapper());
        synchronized (socketLock) {
            connectionSocket = new Socket();
        }

        try {
            Thread.sleep(2000);
        }catch(Exception ex){
            AppLogger.error(TAG, "An error occurred while trying to make a thread sleep", ex);
        }

        try {
            getConnectionSocket().connect(new InetSocketAddress(ipAddress, port), weMessage.CONNECTION_TIMEOUT_WAIT * 1000);

            synchronized (outputStreamLock) {
                outputStream = new ObjectOutputStream(getConnectionSocket().getOutputStream());
            }

            synchronized (inputStreamLock) {
                inputStream = new ObjectInputStream(getConnectionSocket().getInputStream());
            }
        }catch(SocketTimeoutException ex){
            if (isRunning.get()) {
                sendLocalBroadcast(weMessage.INTENT_LOGIN_TIMEOUT, null);
                getParentService().endService();
            }
            return;
        }catch(IOException ex){
            if (isRunning.get()) {
                AppLogger.error(TAG, "An error occurred while connecting to the weServer.", ex);
                sendLocalBroadcast(weMessage.INTENT_LOGIN_ERROR, null);
                getParentService().endService();
            }
            return;
        }

        while (isRunning.get() && !hasTriedAuthenticating.get()){
            try {
                String incoming = (String) getInputStream().readObject();
                if (incoming.startsWith(weMessage.JSON_VERIFY_PASSWORD_SECRET)){
                    ServerMessage message = getIncomingMessage(weMessage.JSON_VERIFY_PASSWORD_SECRET, incoming);
                    JSONEncryptedText secretEncrypted = (JSONEncryptedText) message.getOutgoing(JSONEncryptedText.class, byteArrayAdapter);

                    DecryptionTask secretDecryptionTask = new DecryptionTask(new KeyTextPair(secretEncrypted.getEncryptedText(), secretEncrypted.getKey()), CryptoType.AES);
                    EncryptionTask emailEncryptionTask = new EncryptionTask(emailPlainText, null, CryptoType.AES);

                    secretDecryptionTask.runDecryptTask();
                    emailEncryptionTask.runEncryptTask();

                    String secretString = secretDecryptionTask.getDecryptedText();

                    String hashedPass;

                    if (passwordPlainText == null){
                        hashedPass = passwordHashedText;
                    }else {
                        EncryptionTask hashPasswordTask = new EncryptionTask(passwordPlainText, secretString, CryptoType.BCRYPT);
                        hashPasswordTask.runEncryptTask();

                        hashedPass = hashPasswordTask.getEncryptedText().getEncryptedText();
                        this.passwordHashedText = hashedPass;
                    }

                    EncryptionTask encryptHashedPasswordTask = new EncryptionTask(hashedPass, null, CryptoType.AES);
                    encryptHashedPasswordTask.runEncryptTask();

                    KeyTextPair encryptedEmail = emailEncryptionTask.getEncryptedText();
                    KeyTextPair encryptedHashedPassword = encryptHashedPasswordTask.getEncryptedText();

                    InitConnect initConnect = new InitConnect(
                            weMessage.WEMESSAGE_BUILD_VERSION,
                            Settings.Secure.getString(getParentService().getContentResolver(), Settings.Secure.ANDROID_ID),
                            KeyTextPair.toEncryptedJSON(encryptedEmail),
                            KeyTextPair.toEncryptedJSON(encryptedHashedPassword),
                            DeviceType.ANDROID.getTypeName()
                    );

                    sendOutgoingMessage(weMessage.JSON_INIT_CONNECT, initConnect, InitConnect.class);
                    hasTriedAuthenticating.set(true);
                }
            }catch(Exception ex){
                AppLogger.error(TAG, "An error occurred while authenticating login information", ex);

                Bundle extras = new Bundle();
                extras.putString(weMessage.BUNDLE_DISCONNECT_REASON_ALTERNATE_MESSAGE, getParentService().getString(R.string.connection_error_authentication_message));
                sendLocalBroadcast(weMessage.BROADCAST_DISCONNECT_REASON_ERROR, extras);
                getParentService().endService();
                return;
            }
        }

        MessageDatabase database = DatabaseManager.getInstance(getParentService()).getMessageDatabase();

        while (isRunning.get()){
            try {
                String incoming = (String) getInputStream().readObject();

                if (incoming.startsWith(weMessage.JSON_SUCCESSFUL_CONNECTION)){
                    isConnected.set(true);
                    Account currentAccount = new Account().setEmail(emailPlainText).setEncryptedPassword(passwordHashedText);

                    if (database.getAccountByEmail(emailPlainText) == null){
                        currentAccount.setUuid(UUID.randomUUID());

                        database.setCurrentAccount(currentAccount);
                        database.addAccount(currentAccount);
                    }else {
                        UUID oldUUID = database.getAccountByEmail(emailPlainText).getUuid();
                        currentAccount.setUuid(oldUUID);

                        database.setCurrentAccount(currentAccount);
                        database.updateAccount(oldUUID.toString(), currentAccount);
                    }

                    String hostToSave;

                    if (port == weMessage.DEFAULT_PORT){
                        hostToSave = ipAddress;
                    }else {
                        hostToSave = ipAddress + ":" + port;
                    }

                    SharedPreferences.Editor editor = getParentService().getSharedPreferences(weMessage.APP_IDENTIFIER, Context.MODE_PRIVATE).edit();
                    editor.putString(weMessage.SHARED_PREFERENCES_LAST_HOST, hostToSave);
                    editor.putString(weMessage.SHARED_PREFERENCES_LAST_EMAIL, emailPlainText);
                    editor.putString(weMessage.SHARED_PREFERENCES_LAST_HASHED_PASSWORD, passwordHashedText);

                    editor.apply();

                    sendLocalBroadcast(weMessage.BROADCAST_LOGIN_SUCCESSFUL, null);
                } else if (incoming.startsWith(weMessage.JSON_CONNECTION_TERMINATED)) {
                    ServerMessage serverMessage = getIncomingMessage(weMessage.JSON_CONNECTION_TERMINATED, incoming);
                    DisconnectReason disconnectReason = DisconnectReason.fromCode(((Integer) serverMessage.getOutgoing(Integer.class, byteArrayAdapter)));

                    if (disconnectReason == null) {
                        AppLogger.error(TAG, "A null disconnect reason has caused the connection to be dropped", new NullPointerException());
                        Bundle extras = new Bundle();
                        extras.putString(weMessage.BUNDLE_DISCONNECT_REASON_ALTERNATE_MESSAGE, getParentService().getString(R.string.connection_error_unknown_message));
                        sendLocalBroadcast(weMessage.BROADCAST_DISCONNECT_REASON_ERROR, extras);
                        getParentService().endService();
                    } else {
                        switch (disconnectReason) {
                            case ALREADY_CONNECTED:
                                sendLocalBroadcast(weMessage.BROADCAST_DISCONNECT_REASON_ALREADY_CONNECTED, null);
                                getParentService().endService();
                                break;
                            case INVALID_LOGIN:
                                sendLocalBroadcast(weMessage.BROADCAST_DISCONNECT_REASON_INVALID_LOGIN, null);
                                getParentService().endService();
                                break;
                            case SERVER_CLOSED:
                                sendLocalBroadcast(weMessage.BROADCAST_DISCONNECT_REASON_SERVER_CLOSED, null);
                                getParentService().endService();
                                break;
                            case ERROR:
                                Bundle extras = new Bundle();
                                extras.putString(weMessage.BUNDLE_DISCONNECT_REASON_ALTERNATE_MESSAGE, getParentService().getString(R.string.connection_error_server_side_message));
                                sendLocalBroadcast(weMessage.BROADCAST_DISCONNECT_REASON_ERROR, extras);
                                getParentService().endService();
                                break;
                            case FORCED:
                                sendLocalBroadcast(weMessage.BROADCAST_DISCONNECT_REASON_FORCED, null);
                                getParentService().endService();
                                break;
                            case CLIENT_DISCONNECTED:
                                sendLocalBroadcast(weMessage.BROADCAST_DISCONNECT_REASON_CLIENT_DISCONNECTED, null);
                                getParentService().endService();
                                break;
                            case INCORRECT_VERSION:
                                sendLocalBroadcast(weMessage.BROADCAST_DISCONNECT_REASON_INCORRECT_VERSION, null);
                                getParentService().endService();
                                break;
                        }
                    }
                }else if (incoming.startsWith(weMessage.JSON_NEW_MESSAGE)){



                }

                //TODO: More stuff
            }catch(Exception ex){
                Bundle extras = new Bundle();
                extras.putString(weMessage.BUNDLE_DISCONNECT_REASON_ALTERNATE_MESSAGE, getParentService().getString(R.string.connection_error_unknown_message));
                sendLocalBroadcast(weMessage.BROADCAST_DISCONNECT_REASON_ERROR, extras);
                AppLogger.error(TAG, "An unknown error occurred. Dropping connection to weServer", ex);
                getParentService().endService();
            }
        }
    }

    protected void endConnection(){
        if (isRunning.get()) {
            isRunning.set(false);

            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        sendOutgoingMessage(weMessage.JSON_CONNECTION_TERMINATED, DisconnectReason.CLIENT_DISCONNECTED.getCode(), Integer.class);
                    }catch(Exception ex){
                        AppLogger.error(TAG, "An error occurred while sending disconnect message to the server.", ex);
                    }
                    try {
                        if (isConnected.get()) {
                            isConnected.set(false);
                            getInputStream().close();
                            getOutputStream().close();
                        }
                        getConnectionSocket().close();
                    } catch (Exception ex) {
                        AppLogger.error(TAG, "An error occurred while terminating the connection to the weServer.", ex);
                        interrupt();
                    }
                }
            }).start();
        }
        interrupt();
    }

    private void sendLocalBroadcast(String action, Bundle extras){
        Intent timeoutIntent = new Intent(action);

        if (extras != null) {
            timeoutIntent.putExtras(extras);
        }
        LocalBroadcastManager.getInstance(getParentService()).sendBroadcast(timeoutIntent);
    }
}