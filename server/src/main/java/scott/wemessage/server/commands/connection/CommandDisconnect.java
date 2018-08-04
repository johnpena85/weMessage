package scott.wemessage.server.commands.connection;

import scott.wemessage.commons.types.DisconnectReason;
import scott.wemessage.server.ServerLogger;
import scott.wemessage.server.commands.CommandManager;
import scott.wemessage.server.connection.Device;

public class CommandDisconnect extends ConnectionCommand {

    public CommandDisconnect(CommandManager manager){
        super(manager, "disconnect", "Disconnects a device from the weServer", new String[]{ "killdevice" });
    }

    public void execute(String[] args){
        if (args.length == 0){
            ServerLogger.log("Please provide an IP of a device to disconnect.");
            return;
        }
        Device device = getDeviceManager().getDeviceByAddress(args[0]);

        if(device == null){
            ServerLogger.log("Device with IP Address: " + args[0] + " is not connected.");
            return;
        }
        getDeviceManager().removeDevice(device, DisconnectReason.FORCED, null);
    }
}