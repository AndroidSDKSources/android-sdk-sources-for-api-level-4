/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.bluetooth;

import android.os.RemoteException;
import android.util.Log;

import java.io.UnsupportedEncodingException;

/**
 * The Android Bluetooth API is not finalized, and *will* change. Use at your
 * own risk.
 *
 * Manages the local Bluetooth device. Scan for devices, create bondings,
 * power up and down the adapter.
 *
 * @hide
 */
public class BluetoothDevice {

    public static final int BLUETOOTH_STATE_OFF = 0;
    public static final int BLUETOOTH_STATE_TURNING_ON = 1;
    public static final int BLUETOOTH_STATE_ON = 2;
    public static final int BLUETOOTH_STATE_TURNING_OFF = 3;

    /** Inquiry scan and page scan are both off.
     *  Device is neither discoverable nor connectable */
    public static final int SCAN_MODE_NONE = 0;
    /** Page scan is on, inquiry scan is off.
     *  Device is connectable, but not discoverable */
    public static final int SCAN_MODE_CONNECTABLE = 1;
    /** Page scan and inquiry scan are on.
     *  Device is connectable and discoverable */
    public static final int SCAN_MODE_CONNECTABLE_DISCOVERABLE = 3;

    public static final int RESULT_FAILURE = -1;
    public static final int RESULT_SUCCESS = 0;

    /** We do not have a link key for the remote device, and are therefore not
     * bonded */
    public static final int BOND_NOT_BONDED = 0;
    /** We have a link key for the remote device, and are probably bonded. */
    public static final int BOND_BONDED = 1;
    /** We are currently attempting bonding */
    public static final int BOND_BONDING = 2;

    //TODO: Unify these result codes in BluetoothResult or BluetoothError
    /** A bond attempt failed because pins did not match, or remote device did
     * not respond to pin request in time */
    public static final int UNBOND_REASON_AUTH_FAILED = 1;
    /** A bond attempt failed because the other side explicilty rejected
     * bonding */
    public static final int UNBOND_REASON_AUTH_REJECTED = 2;
    /** A bond attempt failed because we canceled the bonding process */
    public static final int UNBOND_REASON_AUTH_CANCELED = 3;
    /** A bond attempt failed because we could not contact the remote device */
    public static final int UNBOND_REASON_REMOTE_DEVICE_DOWN = 4;
    /** A bond attempt failed because a discovery is in progress */
    public static final int UNBOND_REASON_DISCOVERY_IN_PROGRESS = 5;
    /** An existing bond was explicitly revoked */
    public static final int UNBOND_REASON_REMOVED = 6;

    private static final String TAG = "BluetoothDevice";
    
    private final IBluetoothDevice mService;
    /**
     * @hide - hide this because it takes a parameter of type
     * IBluetoothDevice, which is a System private class.
     * Also note that Context.getSystemService is a factory that
     * returns a BlueToothDevice. That is the right way to get
     * a BluetoothDevice.
     */
    public BluetoothDevice(IBluetoothDevice service) {
        mService = service;
    }

    /**
     * Is Bluetooth currently turned on.
     *
     * @return true if Bluetooth enabled, false otherwise.
     */
    public boolean isEnabled() {
        try {
            return mService.isEnabled();
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return false;
    }

    /**
     * Get the current state of Bluetooth.
     *
     * @return One of BLUETOOTH_STATE_ or BluetoothError.ERROR.
     */
    public int getBluetoothState() {
        try {
            return mService.getBluetoothState();
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return BluetoothError.ERROR;
    }

    /**
     * Enable the Bluetooth device.
     * Turn on the underlying hardware.
     * This is an asynchronous call,
     * BluetoothIntent.BLUETOOTH_STATE_CHANGED_ACTION can be used to check if
     * and when the device is sucessfully enabled.
     * @return false if we cannot enable the Bluetooth device. True does not
     * imply the device was enabled, it only implies that so far there were no
     * problems.
     */
    public boolean enable() {
        try {
            return mService.enable();
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return false;
    }

    /**
     * Disable the Bluetooth device.
     * This turns off the underlying hardware.
     *
     * @return true if successful, false otherwise.
     */
    public boolean disable() {
        try {
            return mService.disable(true);
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return false;
    }

    public String getAddress() {
        try {
            return mService.getAddress();
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return null;
    }

    /**
     * Get the friendly Bluetooth name of this device.
     *
     * This name is visible to remote Bluetooth devices. Currently it is only
     * possible to retrieve the Bluetooth name when Bluetooth is enabled.
     *
     * @return the Bluetooth name, or null if there was a problem.
     */
    public String getName() {
        try {
            return mService.getName();
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return null;
    }

    /**
     * Set the friendly Bluetooth name of this device.
     *
     * This name is visible to remote Bluetooth devices. The Bluetooth Service
     * is responsible for persisting this name.
     *
     * @param name the name to set
     * @return     true, if the name was successfully set. False otherwise.
     */
    public boolean setName(String name) {
        try {
            return mService.setName(name);
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return false;
    }

    public String getVersion() {
        try {
            return mService.getVersion();
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return null;
    }
    public String getRevision() {
        try {
            return mService.getRevision();
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return null;
    }
    public String getManufacturer() {
        try {
            return mService.getManufacturer();
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return null;
    }
    public String getCompany() {
        try {
            return mService.getCompany();
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return null;
    }

    /**
     * Get the current scan mode.
     * Used to determine if the local device is connectable and/or discoverable
     * @return Scan mode, one of SCAN_MODE_* or an error code
     */
    public int getScanMode() {
        try {
            return mService.getScanMode();
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return BluetoothError.ERROR_IPC;
    }

    /**
     * Set the current scan mode.
     * Used to make the local device connectable and/or discoverable
     * @param scanMode One of SCAN_MODE_*
     */
    public void setScanMode(int scanMode) {
        try {
            mService.setScanMode(scanMode);
        } catch (RemoteException e) {Log.e(TAG, "", e);}
    }

    public int getDiscoverableTimeout() {
        try {
            return mService.getDiscoverableTimeout();
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return -1;
    }
    public void setDiscoverableTimeout(int timeout) {
        try {
            mService.setDiscoverableTimeout(timeout);
        } catch (RemoteException e) {Log.e(TAG, "", e);}
    }

    public boolean startDiscovery() {
        return startDiscovery(true);
    }
    public boolean startDiscovery(boolean resolveNames) {
        try {
            return mService.startDiscovery(resolveNames);
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return false;
    }

    public void cancelDiscovery() {
        try {
            mService.cancelDiscovery();
        } catch (RemoteException e) {Log.e(TAG, "", e);}
    }

    public boolean isDiscovering() {
        try {
            return mService.isDiscovering();
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return false;
    }

    public boolean startPeriodicDiscovery() {
        try {
            return mService.startPeriodicDiscovery();
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return false;
    }
    public boolean stopPeriodicDiscovery() {
        try {
            return mService.stopPeriodicDiscovery();
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return false;
    }
    public boolean isPeriodicDiscovery() {
        try {
            return mService.isPeriodicDiscovery();
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return false;
    }

    public String[] listRemoteDevices() {
        try {
            return mService.listRemoteDevices();
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return null;
    }

    /**
     * List remote devices that have a low level (ACL) connection.
     *
     * RFCOMM, SDP and L2CAP are all built on ACL connections. Devices can have
     * an ACL connection even when not paired - this is common for SDP queries
     * or for in-progress pairing requests.
     *
     * In most cases you probably want to test if a higher level protocol is
     * connected, rather than testing ACL connections.
     *
     * @return bluetooth hardware addresses of remote devices with a current
     *         ACL connection. Array size is 0 if no devices have a
     *         connection. Null on error.
     */
    public String[] listAclConnections() {
        try {
            return mService.listAclConnections();
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return null;
    }

    /**
     * Check if a specified remote device has a low level (ACL) connection.
     *
     * RFCOMM, SDP and L2CAP are all built on ACL connections. Devices can have
     * an ACL connection even when not paired - this is common for SDP queries
     * or for in-progress pairing requests.
     *
     * In most cases you probably want to test if a higher level protocol is
     * connected, rather than testing ACL connections.
     *
     * @param address the Bluetooth hardware address you want to check.
     * @return true if there is an ACL connection, false otherwise and on
     *         error.
     */
    public boolean isAclConnected(String address) {
        try {
            return mService.isAclConnected(address);
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return false;
    }

    /**
     * Perform a low level (ACL) disconnection of a remote device.
     *
     * This forcably disconnects the ACL layer connection to a remote device,
     * which will cause all RFCOMM, SDP and L2CAP connections to this remote
     * device to close.
     *
     * @param address the Bluetooth hardware address you want to disconnect.
     * @return true if the device was disconnected, false otherwise and on
     *         error.
     */
    public boolean disconnectRemoteDeviceAcl(String address) {
        try {
            return mService.disconnectRemoteDeviceAcl(address);
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return false;
    }

    /**
     * Create a bonding with a remote bluetooth device.
     *
     * This is an asynchronous call. The result of this bonding attempt can be
     * observed through BluetoothIntent.BOND_STATE_CHANGED_ACTION intents.
     *
     * @param address the remote device Bluetooth address.
     * @return false If there was an immediate problem creating the bonding,
     *         true otherwise.
     */
    public boolean createBond(String address) {
        try {
            return mService.createBond(address);
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return false;
    }

    /**
     * Cancel an in-progress bonding request started with createBond.
     */
    public boolean cancelBondProcess(String address) {
        try {
            return mService.cancelBondProcess(address);
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return false;
    }

    /**
     * Remove an already exisiting bonding (delete the link key).
     */
    public boolean removeBond(String address) {
        try {
            return mService.removeBond(address);
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return false;
    }

    /**
     * List remote devices that are bonded (paired) to the local device.
     *
     * Bonding (pairing) is the process by which the user enters a pin code for
     * the device, which generates a shared link key, allowing for
     * authentication and encryption of future connections. In Android we
     * require bonding before RFCOMM or SCO connections can be made to a remote
     * device.
     *
     * This function lists which remote devices we have a link key for. It does
     * not cause any RF transmission, and does not check if the remote device
     * still has it's link key with us. If the other side no longer has its
     * link key then the RFCOMM or SCO connection attempt will result in an
     * error.
     *
     * This function does not check if the remote device is in range.
     *
     * Remote devices that have an in-progress bonding attempt are not
     * returned.
     *
     * @return bluetooth hardware addresses of remote devices that are
     *         bonded. Array size is 0 if no devices are bonded. Null on error.
     */
    public String[] listBonds() {
        try {
            return mService.listBonds();
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return null;
    }

    /**
     * Get the bonding state of a remote device.
     *
     * Result is one of:
     * BluetoothError.*
     * BOND_*
     *
     * @param address Bluetooth hardware address of the remote device to check.
     * @return Result code
     */
    public int getBondState(String address) {
        try {
            return mService.getBondState(address);
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return BluetoothError.ERROR_IPC;
    }

    public String getRemoteName(String address) {
        try {
            return mService.getRemoteName(address);
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return null;
    }

    public String getRemoteVersion(String address) {
        try {
            return mService.getRemoteVersion(address);
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return null;
    }
    public String getRemoteRevision(String address) {
        try {
            return mService.getRemoteRevision(address);
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return null;
    }
    public String getRemoteManufacturer(String address) {
        try {
            return mService.getRemoteManufacturer(address);
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return null;
    }
    public String getRemoteCompany(String address) {
        try {
            return mService.getRemoteCompany(address);
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return null;
    }

    /**
     * Returns the RFCOMM channel associated with the 16-byte UUID on
     * the remote Bluetooth address.
     *
     * Performs a SDP ServiceSearchAttributeRequest transaction. The provided
     * uuid is verified in the returned record. If there was a problem, or the
     * specified uuid does not exist, -1 is returned.
     */
    public boolean getRemoteServiceChannel(String address, short uuid16,
            IBluetoothDeviceCallback callback) {
        try {
            return mService.getRemoteServiceChannel(address, uuid16, callback);
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return false;
    }

    /**
     * Get the major, minor and servics classes of a remote device.
     * These classes are encoded as a 32-bit integer. See BluetoothClass.
     * @param address remote device
     * @return 32-bit class suitable for use with BluetoothClass, or
     *         BluetoothClass.ERROR on error
     */
    public int getRemoteClass(String address) {
        try {
            return mService.getRemoteClass(address);
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return BluetoothClass.ERROR;
    }

    public byte[] getRemoteFeatures(String address) {
        try {
            return mService.getRemoteFeatures(address);
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return null;
    }
    public String lastSeen(String address) {
        try {
            return mService.lastSeen(address);
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return null;
    }
    public String lastUsed(String address) {
        try {
            return mService.lastUsed(address);
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return null;
    }

    public boolean setPin(String address, byte[] pin) {
        try {
            return mService.setPin(address, pin);
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return false;
    }
    public boolean cancelPin(String address) {
        try {
            return mService.cancelPin(address);
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return false;
    }

    /**
     * Check that a pin is valid and convert to byte array.
     *
     * Bluetooth pin's are 1 to 16 bytes of UTF8 characters.
     * @param pin pin as java String
     * @return the pin code as a UTF8 byte array, or null if it is an invalid
     *         Bluetooth pin.
     */
    public static byte[] convertPinToBytes(String pin) {
        if (pin == null) {
            return null;
        }
        byte[] pinBytes;
        try {
            pinBytes = pin.getBytes("UTF8");
        } catch (UnsupportedEncodingException uee) {
            Log.e(TAG, "UTF8 not supported?!?");  // this should not happen
            return null;
        }
        if (pinBytes.length <= 0 || pinBytes.length > 16) {
            return null;
        }
        return pinBytes;
    }

    private static final int ADDRESS_LENGTH = 17;
    /** Sanity check a bluetooth address, such as "00:43:A8:23:10:F0" */
    public static boolean checkBluetoothAddress(String address) {
        if (address == null || address.length() != ADDRESS_LENGTH) {
            return false;
        }
        for (int i = 0; i < ADDRESS_LENGTH; i++) {
            char c = address.charAt(i);
            switch (i % 3) {
            case 0:
            case 1:
                if (Character.digit(c, 16) != -1) {
                    break;  // hex character, OK
                }
                return false;
            case 2:
                if (c == ':') {
                    break;  // OK
                }
                return false;
            }
        }
        return true;
    }
}
