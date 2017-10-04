package scott.wemessage.commons.connection.json.message;

public class JSONNotification {

    private String notificationVersion;
    private String registrationToken;
    private String encryptedText;
    private String key;
    private String handleId;
    private String chatId;
    private String chatName;
    private String attachmentNumber;

    public JSONNotification(){

    }

    public JSONNotification(String notificationVersion, String registrationToken, String encryptedText, String key, String handleId, String chatId, String chatName, String attachmentNumber) {
        this.notificationVersion = notificationVersion;
        this.registrationToken = registrationToken;
        this.encryptedText = encryptedText;
        this.key = key;
        this.handleId = handleId;
        this.chatId = chatId;
        this.chatName = chatName;
        this.attachmentNumber = attachmentNumber;
    }

    public String getNotificationVersion() {
        return notificationVersion;
    }

    public String getRegistrationToken() {
        return registrationToken;
    }

    public String getEncryptedText() {
        return encryptedText;
    }

    public String getKey() {
        return key;
    }

    public String getHandleId() {
        return handleId;
    }

    public String getChatId() {
        return chatId;
    }

    public String getChatName() {
        return chatName;
    }

    public String getAttachmentNumber() {
        return attachmentNumber;
    }

    public void setNotificationVersion(String notificationVersion) {
        this.notificationVersion = notificationVersion;
    }

    public void setRegistrationToken(String registrationToken) {
        this.registrationToken = registrationToken;
    }

    public void setEncryptedText(String encryptedText) {
        this.encryptedText = encryptedText;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public void setHandleId(String handleId) {
        this.handleId = handleId;
    }

    public void setChatId(String chatId) {
        this.chatId = chatId;
    }

    public void setChatName(String chatName) {
        this.chatName = chatName;
    }

    public void setAttachmentNumber(String attachmentNumber) {
        this.attachmentNumber = attachmentNumber;
    }
}