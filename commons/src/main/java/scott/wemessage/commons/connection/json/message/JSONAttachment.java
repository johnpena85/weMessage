package scott.wemessage.commons.connection.json.message;

import com.google.gson.annotations.SerializedName;

public class JSONAttachment {

    @SerializedName("uuid")
    private String uuid;

    @SerializedName("macGuid")
    private String macGuid;

    @SerializedName("transferName")
    private String transferName;

    @SerializedName("fileType")
    private String fileType;

    @SerializedName("totalBytes")
    private long totalBytes;

    public JSONAttachment(String uuid, String macGuid, String transferName, String fileType, long totalBytes){
        this.uuid = uuid;
        this.macGuid = macGuid;
        this.transferName = transferName;
        this.fileType = fileType;
        this.totalBytes = totalBytes;
    }

    public String getUuid(){
        return uuid;
    }

    public String getMacGuid() {
        return macGuid;
    }

    public String getTransferName() {
        return transferName;
    }

    public String getFileType() {
        return fileType;
    }

    public long getTotalBytes() {
        return totalBytes;
    }

    public void setUuid(String uuid){
        this.uuid = uuid;
    }

    public void setMacGuid(String macGuid) {
        this.macGuid = macGuid;
    }

    public void setTransferName(String transferName) {
        this.transferName = transferName;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public void setTotalBytes(long totalBytes) {
        this.totalBytes = totalBytes;
    }
}