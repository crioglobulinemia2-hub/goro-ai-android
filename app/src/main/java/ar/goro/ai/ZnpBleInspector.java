package ar.goro.ai;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import java.util.ArrayList;
import java.util.List;

/**
 * Inventario pasivo de servicios GATT ya descubiertos.
 * No conecta, no escribe, no autentica y no modifica el dispositivo.
 * No registra MAC ni identificadores personales.
 */
public final class ZnpBleInspector {
    private ZnpBleInspector() {}
    public static List<String> describe(BluetoothGatt gatt) {
        List<String> results = new ArrayList<>();
        if (gatt == null) return results;
        for (BluetoothGattService service : gatt.getServices()) {
            results.add("Servicio: " + service.getUuid());
            for (BluetoothGattCharacteristic c : service.getCharacteristics()) {
                int p = c.getProperties();
                results.add("  Característica: " + c.getUuid()
                    + " read=" + ((p & BluetoothGattCharacteristic.PROPERTY_READ) != 0)
                    + " notify=" + ((p & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0)
                    + " write=" + ((p & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0));
            }
        }
        return results;
    }
}
