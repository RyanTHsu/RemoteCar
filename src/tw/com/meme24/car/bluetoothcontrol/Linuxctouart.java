package tw.com.meme24.car.bluetoothcontrol;

import android.util.Log;

public class Linuxctouart {
	static
	{
		try
		{
			System.loadLibrary("uart_control");
			Log.i("JIN","Trying to load libuart_control.so");
		}
		catch(UnsatisfiedLinkError ule)
		{
			Log.e("JIN","WARNING:could not load libuart_control.so");
		}
	}
	public static native int openUart(int i);	  
	public static native void closeUart(int i);
	public static native int setUart(int i);
	public static native int sendMsgUart(String msg);
	public static native int  receiveMsgUart();
	
}
