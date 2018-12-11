package tech.gusavila92.websocketclient.common;

/**
 * Utility class
 * 
 * @author Gustavo Avila
 *
 */
public class Utils {
	/**
	 * Converts the int value passed as parameter to a 2 byte array
	 * 
	 * @param value
	 * @return
	 */
	public static byte[] to2ByteArray(int value) {
		return new byte[] { (byte) (value >>> 8), (byte) value };
	}

	/**
	 * Converts the int value passed as parameter to a 8 byte array.
	 * Even though the specification allows payloads with sizes greater than 32 bits,
	 * Java only allows integers with 32 bit size, so the first 4 bytes will be zeroes.
	 * 
	 * @param value
	 * @return
	 */
	public static byte[] to8ByteArray(int value) {
		return new byte[] { 0, 0, 0, 0,
				(byte) (value >>> 24), (byte) (value >>> 16), (byte) (value >>> 8), (byte) value };
	}

	/**
	 * Converts the byte array passed as parameter to an integer
	 * 
	 * @param bytes
	 * @return
	 */
	public static int fromByteArray(byte[] bytes) {
		return bytes[0] << 24 | (bytes[1] & 0xFF) << 16 | (bytes[2] & 0xFF) << 8 | (bytes[3] & 0xFF);
	}
}
