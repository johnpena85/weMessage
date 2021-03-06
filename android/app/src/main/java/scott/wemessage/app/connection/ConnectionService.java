/*
 *  weMessage - iMessage for Android
 *  Copyright (C) 2018 Roman Scott
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as
 *  published by the Free Software Foundation, either version 3 of the
 *  License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package scott.wemessage.app.connection;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.AndroidRuntimeException;

import scott.wemessage.app.weMessage;

public class ConnectionService extends Service {

    protected static final String TAG = "Connection Service";
    private final Object connectionHandlerLock = new Object();
    private final IBinder binder = new ConnectionServiceBinder();

    private ConnectionHandler connectionHandler;

    public ConnectionHandler getConnectionHandler(){
        synchronized (connectionHandlerLock){
            return connectionHandler;
        }
    }

    @Override
    public void onCreate(){
        super.onCreate();
    }

    @Override
    public void onDestroy() {
        if (getConnectionHandler() == null) return;

        if (getConnectionHandler().isRunning().get()){
            Intent serviceClosedIntent = new Intent(weMessage.BROADCAST_CONNECTION_SERVICE_STOPPED);
            LocalBroadcastManager.getInstance(this).sendBroadcast(serviceClosedIntent);

            getConnectionHandler().endConnection();
        }
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (getConnectionHandler() != null && getConnectionHandler().isRunning().get()){
            throw new ConnectionException("There is already a connection to the weServer established.");
        }

        synchronized (connectionHandlerLock){
            ConnectionHandler connectionHandler = new ConnectionHandler(this,
                    intent.getStringExtra(weMessage.ARG_HOST),
                    intent.getIntExtra(weMessage.ARG_PORT, -1),
                    intent.getStringExtra(weMessage.ARG_EMAIL),
                    intent.getStringExtra(weMessage.ARG_PASSWORD),
                    intent.getBooleanExtra(weMessage.ARG_PASSWORD_ALREADY_HASHED, false),
                    intent.getStringExtra(weMessage.ARG_FAILOVER_IP));

            connectionHandler.start();
            this.connectionHandler = connectionHandler;
        }

        return START_REDELIVER_INTENT;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public void endService(){
        Intent serviceClosedIntent = new Intent();
        serviceClosedIntent.setAction(weMessage.BROADCAST_CONNECTION_SERVICE_STOPPED);
        LocalBroadcastManager.getInstance(this).sendBroadcast(serviceClosedIntent);
        getConnectionHandler().endConnection();
        stopSelf();
    }

    public class ConnectionServiceBinder extends Binder {

        public ConnectionService getService(){
            return ConnectionService.this;
        }
    }

    public static class ConnectionException extends AndroidRuntimeException {
        public ConnectionException(String name) {
            super(name);
        }

        public ConnectionException(String name, Throwable cause) {
            super(name, cause);
        }

        public ConnectionException(Exception cause) {
            super(cause);
        }
    }
}