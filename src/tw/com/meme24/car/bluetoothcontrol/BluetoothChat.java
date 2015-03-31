package tw.com.meme24.car.bluetoothcontrol;

import java.io.IOException;

import tw.com.meme24.car.bluetoothcontrol.R;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * This is the main Activity that displays the current chat session.
 */
public class BluetoothChat extends Activity {
    // Debugging
    private static final String TAG = "BluetoothCar";
    private static final boolean D = true;
    private static boolean brain_mode_on = false;

    // Message types sent from the BluetoothChatService Handler
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;

    // Key names received from the BluetoothChatService Handler
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";

    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;

    // Layout Views
    private TextView mTitle;
    //private ListView mConversationView;
    //private EditText mOutEditText;
    
    private Button f_btn;
	private Button l_btn;
	private Button r_btn;
	private Button b_btn;
	private Button auto_btn;
	private Button brain_btn;
	private Button exit_btn;
	private EditText et_delay;
	private EditText et_f;
	private EditText et_b;
	private EditText et_l;
	private EditText et_r;
	private Integer delay_time;
	private Integer front_steps;
	private Integer back_steps;
	private Integer left_steps;
	private Integer right_steps;
	private static int r_fun=-1;
	private static int l_fun=-1;
	public boolean sign1;
	private Integer msg;
    private TextView mText;
    private TextView tv_brain;
    private TextView tv_bresult;
    // Name of the connected device
    private String mConnectedDeviceName = null;
    // Array adapter for the conversation thread
    //private ArrayAdapter<String> mConversationArrayAdapter;
    // String buffer for outgoing messages
    private StringBuffer mOutStringBuffer;
    // Local Bluetooth adapter
    private BluetoothAdapter mBluetoothAdapter = null;
    // Member object for the chat services
    private BluetoothChatService mChatService = null;
    
    Handler brain_handler=new Handler();
    int fd;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(D) Log.e(TAG, "+++ ON CREATE +++");

        // Set up the window layout
        requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);
        setContentView(R.layout.main);
        getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE, R.layout.custom_title);
        mText = (TextView) findViewById(R.id.textView1);
        // Set up the custom title
        mTitle = (TextView) findViewById(R.id.title_left_text);
        mTitle.setText(R.string.app_name);
        mTitle = (TextView) findViewById(R.id.title_right_text);
        
        //Motor control layout
        f_btn = (Button)findViewById(R.id.forward);
		b_btn = (Button)findViewById(R.id.back);
		r_btn = (Button)findViewById(R.id.right);
		l_btn = (Button)findViewById(R.id.left);
		auto_btn = (Button)findViewById(R.id.auto_mode);
		brain_btn = (Button)findViewById(R.id.btn_brain);
		
		exit_btn = (Button)findViewById(R.id.quit);
		et_delay = (EditText)findViewById(R.id.et_delay);
		et_f = (EditText)findViewById(R.id.et_front);
		et_b = (EditText)findViewById(R.id.et_back);
		et_l = (EditText)findViewById(R.id.et_left);
		et_r = (EditText)findViewById(R.id.et_right);
		
		tv_brain = (TextView)findViewById(R.id.tv_brain);
		tv_bresult = (TextView)findViewById(R.id.tv_bresult);
        
		//Set listener
		f_btn.setOnClickListener(motor_run);
		b_btn.setOnClickListener(motor_run);
		l_btn.setOnClickListener(motor_run);
		r_btn.setOnClickListener(motor_run);
		auto_btn.setOnClickListener(motor_run);
		exit_btn.setOnClickListener(motor_close);
		brain_btn.setOnClickListener(brain_mode);
		
        // Get local Bluetooth adapter
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // If the adapter is null, then Bluetooth is not supported
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
    }
    
    //OnClick Area
    private OnClickListener brain_mode = new OnClickListener(){
		@Override
		public void onClick(View v) {
			// TODO Auto-generated method stub
			if(!brain_mode_on){
				fd=Linuxctouart.openUart(1);
				if(fd>0){
					tv_brain.setText("open device sucess!"+String.valueOf(fd));
//{B1200,B2400,B4800,B9600,B19200,B38400,B57600,B115200,B230400,B921600};
// 4 for B19200
					Linuxctouart.setUart(4);
					brain_mode_on=true;
					brain_handler.post(uart_recive);
				}else{
					tv_brain.setText("open device false!"+fd);
					sign1=false;
				}
			}else{
				tv_brain.setText("close device ");
				brain_mode_on=false;
				Linuxctouart.closeUart(0);
			}
		}
	};
    
    private OnClickListener motor_close = new OnClickListener(){
		@Override
		public void onClick(View v) {
			// TODO Auto-generated method stub
			Linuxctouart.closeUart(0);
			finish();
		}
	};
	
	private OnClickListener motor_run = new OnClickListener(){
		// 20 steps
		/* a: directory=>  1:front 2:left 3:right 4:back */
		/*    range 1~4									 */
		/* b: steps=>  front_steps						 */
		/*    range 0~1000								 */
		/* c: delay time=> delay_time					 */
		/*    range 0~400								 */
		/* example: forwarding, 20 steps, delaytime 200  */
		/* msg = 1*10000*1000+20*1000+200                 */
		/*     = 10020200                                */
		//Linuxc.send(d_com, test);
		@Override
		public void onClick(View v) {
			delay_time=Integer.parseInt(et_delay.getText().toString());			
			Button btn = (Button)v;
			
//			forwarding
			if(btn==f_btn){
				front_steps=Integer.parseInt(et_f.getText().toString());
				Log.d(TAG, "Forwarding "+front_steps+" steps");
				
				msg=1*10000*1000+front_steps*1000+delay_time;
				//test.setText(msg.toString());
				sendMessage(msg.toString());
				
					
					
				int dt=Integer.parseInt(msg.toString())%1000;
				int ft=(Integer.parseInt(msg.toString())/1000)%10000;
				int dir=Integer.parseInt(msg.toString())/(10000*1000);
				Log.d(TAG, "dt:"+dt+" ft:"+ft+" dir:"+dir);				
			}
			
//			turning back
			if(btn==b_btn){
				back_steps=Integer.parseInt(et_b.getText().toString());
				Log.d(TAG, "turning back "+back_steps+" steps\n");
				
				msg=4*10000*1000+back_steps*1000+delay_time;
				//test.setText(msg.toString());
				sendMessage(msg.toString());
				
				int dt=Integer.parseInt(msg.toString())%1000;
				int ft=(Integer.parseInt(msg.toString())/1000)%10000;
				int dir=Integer.parseInt(msg.toString())/(10000*1000);
				Log.d(TAG, "dt:"+dt+" ft:"+ft+" dir:"+dir);
			}

//			turn left			
			if(btn==l_btn){
				left_steps=Integer.parseInt(et_l.getText().toString());				
				Log.d(TAG, "turning left "+left_steps+" steps\n");
				
				msg=2*10000*1000+left_steps*1000+delay_time;
				sendMessage(msg.toString());
				
				
				
				int dt=Integer.parseInt(msg.toString())%1000;
				int ft=(Integer.parseInt(msg.toString())/1000)%10000;
				int dir=Integer.parseInt(msg.toString())/(10000*1000);
				Log.d(TAG, "dt:"+dt+" ft:"+ft+" dir:"+dir);
			}
			
//			turn right
			if(btn==r_btn){
				right_steps=Integer.parseInt(et_r.getText().toString());
				Log.d(TAG, "turning left "+left_steps+" steps\n");
				
				msg=3*10000*1000+right_steps*1000+delay_time;
				sendMessage(msg.toString());
				
				
				int dt=Integer.parseInt(msg.toString())%1000;
				int ft=(Integer.parseInt(msg.toString())/1000)%10000;
				int dir=Integer.parseInt(msg.toString())/(10000*1000);
				Log.d(TAG, "dt:"+dt+" ft:"+ft+" dir:"+dir);
			}
			
			if(btn==auto_btn){
				
				Log.d(TAG, "Auto Mode ");
				
				msg=5*10000*1000;
				//test.setText(msg.toString());
				sendMessage(msg.toString());
				
					
					
				int dt=Integer.parseInt(msg.toString())%1000;
				int ft=(Integer.parseInt(msg.toString())/1000)%10000;
				int dir=Integer.parseInt(msg.toString())/(10000*1000);
				Log.d(TAG, "dt:"+dt+" ft:"+ft+" dir:"+dir);				
			}
			
		}		
	};
    
	public int left_function(int interval){
		if(l_fun == -1){
			l_fun=1;
		}else{
			l_fun+=interval;
			if(l_fun<1)l_fun+=4;
			if(l_fun>4)l_fun-=4;
		}
		return l_fun;
	}
	
	public int right_function(int interval){
		if(r_fun == -1){
			r_fun=1;
		}else{
			r_fun+=interval;
			if(r_fun<1)r_fun+=4;
			if(r_fun>4)r_fun-=4;
		}
		return r_fun;
	}
	
    @Override
    public void onStart() {
        super.onStart();
        if(D) Log.e(TAG, "++ ON START ++");

        // If BT is not on, request that it be enabled.
        // setupChat() will then be called during onActivityResult
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        // Otherwise, setup the chat session
        } else {
            if (mChatService == null) setupChat();
        }
    }

    @Override
    public synchronized void onResume() {
        super.onResume();
        if(D) Log.e(TAG, "+ ON RESUME +");

        // Performing this check in onResume() covers the case in which BT was
        // not enabled during onStart(), so we were paused to enable it...
        // onResume() will be called when ACTION_REQUEST_ENABLE activity returns.
        if (mChatService != null) {
            // Only if the state is STATE_NONE, do we know that we haven't started already
            if (mChatService.getState() == BluetoothChatService.STATE_NONE) {
              // Start the Bluetooth chat services
              mChatService.start();
            }
        }
    }

    private void setupChat() {
        Log.d(TAG, "setupChat()");

        // Initialize the array adapter for the conversation thread
        //mConversationArrayAdapter = new ArrayAdapter<String>(this, R.layout.message);
        //mConversationView = (ListView) findViewById(R.id.in);
        //mConversationView.setAdapter(mConversationArrayAdapter);

        // Initialize the compose field with a listener for the return key
        //mOutEditText = (EditText) findViewById(R.id.edit_text_out);
        //mOutEditText.setOnEditorActionListener(mWriteListener);

        // Initialize the send button with a listener that for click events
        
        

        // Initialize the BluetoothChatService to perform bluetooth connections
        mChatService = new BluetoothChatService(this, mHandler);

        // Initialize the buffer for outgoing messages
        mOutStringBuffer = new StringBuffer("");
    }

    @Override
    public synchronized void onPause() {
        super.onPause();
        if(D) Log.e(TAG, "- ON PAUSE -");
    }

    @Override
    public void onStop() {
        super.onStop();
        if(D) Log.e(TAG, "-- ON STOP --");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Stop the Bluetooth chat services
        if (mChatService != null) mChatService.stop();
        if(D) Log.e(TAG, "--- ON DESTROY ---");
    }

    private void ensureDiscoverable() {
        if(D) Log.d(TAG, "ensure discoverable");
        if (mBluetoothAdapter.getScanMode() !=
            BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            startActivity(discoverableIntent);
        }
    }

    /**
     * Sends a message.
     * @param message  A string of text to send.
     */
    private void sendMessage(String message) {
        // Check that we're actually connected before trying anything
        if (mChatService.getState() != BluetoothChatService.STATE_CONNECTED) {
            Toast.makeText(this, R.string.not_connected, Toast.LENGTH_SHORT).show();
            return;
        }

        // Check that there's actually something to send
        if (message.length() > 0) {
            // Get the message bytes and tell the BluetoothChatService to write
            byte[] send = message.getBytes();
            mChatService.write(send);

            // Reset out string buffer to zero and clear the edit text field
            //mOutStringBuffer.setLength(0);
            //mOutEditText.setText(mOutStringBuffer);
        }
    }

    // The action listener for the EditText widget, to listen for the return key
    /*private TextView.OnEditorActionListener mWriteListener =
        new TextView.OnEditorActionListener() {
        public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
            // If the action is a key-up event on the return key, send the message
            if (actionId == EditorInfo.IME_NULL && event.getAction() == KeyEvent.ACTION_UP) {
                String message = view.getText().toString();
                sendMessage(message);
            }
            if(D) Log.i(TAG, "END onEditorAction");
            return true;
        }
    };*/

    // The Handler that gets information back from the BluetoothChatService
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MESSAGE_STATE_CHANGE:
                if(D) Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
                switch (msg.arg1) {
                case BluetoothChatService.STATE_CONNECTED:
                    mTitle.setText(R.string.title_connected_to);
                    mTitle.append(mConnectedDeviceName);
                    //mConversationArrayAdapter.clear();
                    break;
                case BluetoothChatService.STATE_CONNECTING:
                    mTitle.setText(R.string.title_connecting);
                    break;
                case BluetoothChatService.STATE_LISTEN:
                case BluetoothChatService.STATE_NONE:
                    mTitle.setText(R.string.title_not_connected);
                    break;
                }
                break;
            case MESSAGE_WRITE:
                byte[] writeBuf = (byte[]) msg.obj;
                // construct a string from the buffer
                String writeMessage = new String(writeBuf);
                //mConversationArrayAdapter.add("Me:  " + writeMessage);
                break;
            case MESSAGE_READ:
                byte[] readBuf = (byte[]) msg.obj;
                // construct a string from the valid bytes in the buffer
                String readMessage = new String(readBuf, 0, msg.arg1);
                mText.append(readMessage);
                //mConversationArrayAdapter.add(mConnectedDeviceName+":  " + readMessage);
                
                break;
            case MESSAGE_DEVICE_NAME:
                // save the connected device's name
                mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
                Toast.makeText(getApplicationContext(), "Connected to "
                               + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                break;
            case MESSAGE_TOAST:
                Toast.makeText(getApplicationContext(), msg.getData().getString(TOAST),
                               Toast.LENGTH_SHORT).show();
                break;
            }
        }
    };

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(D) Log.d(TAG, "onActivityResult " + resultCode);
        switch (requestCode) {
        case REQUEST_CONNECT_DEVICE:
            // When DeviceListActivity returns with a device to connect
            if (resultCode == Activity.RESULT_OK) {
                // Get the device MAC address
                String address = data.getExtras()
                                     .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
                // Get the BLuetoothDevice object
                BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
                // Attempt to connect to the device
                mChatService.connect(device);
            }
            break;
        case REQUEST_ENABLE_BT:
            // When the request to enable Bluetooth returns
            if (resultCode == Activity.RESULT_OK) {
                // Bluetooth is now enabled, so set up a chat session
                setupChat();
            } else {
                // User did not enable Bluetooth or an error occured
                Log.d(TAG, "BT not enabled");
                Toast.makeText(this, R.string.bt_not_enabled_leaving, Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.option_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.scan:
            // Launch the DeviceListActivity to see devices and do scan
            Intent serverIntent = new Intent(this, DeviceListActivity.class);
            startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
            return true;
        case R.id.discoverable:
            // Ensure this device is discoverable by others
            ensureDiscoverable();
            return true;
        }
        return false;
    }
    /*         Brain mode         */
    final Runnable uart_recive = new Runnable(){
		@Override
		public void run() {
			// TODO Auto-generated method stub
			int value;
			
			value=Linuxctouart.receiveMsgUart();
			if(value>0){
				tv_bresult.setText("reslut:"+String.valueOf(value));
				delay_time=Integer.parseInt(et_delay.getText().toString());
				if(value>128){
					front_steps=Integer.parseInt(et_f.getText().toString());
					Log.d(TAG, "Braind Mode Forwarding "+front_steps+" steps");
					
					msg=1*10000*1000+front_steps*1000+delay_time;
					//test.setText(msg.toString());
					sendMessage(msg.toString());
				}else{
					left_steps=Integer.parseInt(et_l.getText().toString());
					Log.d(TAG, "Braind Mode Turnning Left "+left_steps+" steps");
					
					msg=2*10000*1000+left_steps*1000+delay_time;
					//test.setText(msg.toString());
					sendMessage(msg.toString());
				}
			}
			if(brain_mode_on)brain_handler.postDelayed(uart_recive,10);
		}
	};


}