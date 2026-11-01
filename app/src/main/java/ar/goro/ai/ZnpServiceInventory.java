package ar.goro.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Pure, testable classification of observed BLE GATT UUIDs. No writes or pairing. */
public final class ZnpServiceInventory {
    public static final UUID GENERIC_ACCESS=UUID.fromString("00001800-0000-1000-8000-00805f9b34fb");
    public static final UUID GENERIC_ATTRIBUTE=UUID.fromString("00001801-0000-1000-8000-00805f9b34fb");
    public static final UUID BATTERY=UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb");
    private ZnpServiceInventory(){}
    public static String classify(UUID id) {
        if(id==null)return "INVALID";
        if(id.equals(GENERIC_ACCESS))return "GENERIC_ACCESS";
        if(id.equals(GENERIC_ATTRIBUTE))return "GENERIC_ATTRIBUTE";
        if(id.equals(BATTERY))return "BATTERY";
        return "UNIDENTIFIED";
    }
    public static List<String> summarize(List<UUID> services) {
        if(services==null)return Collections.emptyList();
        List<String> result=new ArrayList<>();
        for(UUID id:services)result.add(classify(id));
        return result;
    }
    public static boolean rcspVerified(List<UUID> services) {
        // A service UUID alone cannot prove a vendor protocol or authentication.
        return false;
    }
}
