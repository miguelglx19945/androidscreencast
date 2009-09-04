package net.srcz.android.screencast.injector;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;

import com.android.ddmlib.Device;
import com.android.ddmlib.SyncService.SyncResult;

public class Injector {
	private static final int PORT = 1324;
	private static final String LOCAL_AGENT_JAR_LOCATION = "MyInjectEventapp.jar";
	private static final String REMOTE_AGENT_JAR_LOCATION = "/data/MyInjectEventapp.jar";
	private static final String AGENT_MAIN_CLASS = "net.srcz.android.screencast.client.Main";
	Device device;

	Socket s;
	OutputStream os;

	public Injector(Device d) throws IOException {
		this.device = d;

		device.createForward(PORT, PORT);

		if (killRunningAgent())
			System.out.println("Old client closed");
		uploadAgent();

		try {
			Thread.sleep(1000);
			startAgent();
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		s = new Socket("127.0.0.1", PORT);
		os = s.getOutputStream();
		System.out.println("succes !");
	}

	private void startAgent() {
		Thread t = new Thread() {
			public void run() {
				try {
					launchProg("" + PORT);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		};
		t.start();
	}

	private void uploadAgent() {
		SyncResult result = device.getSyncService().pushFile(
				LOCAL_AGENT_JAR_LOCATION, REMOTE_AGENT_JAR_LOCATION,
				new NullSyncProgressMonitor());
		if (result.getCode() != 0)
			throw new RuntimeException("code = " + result.getCode()
					+ " message= " + result.getMessage());
	}

	/**
	 * @return true if there was a client running
	 */
	private boolean killRunningAgent() {
		try {
			Socket s = new Socket("127.0.0.1", PORT);
			OutputStream os = s.getOutputStream();
			os.write("quit\n".getBytes());
			os.flush();
			os.close();
			s.close();
			return true;
		} catch (Exception ex) {
			// ignor�
		}
		return false;
	}

	public void close() {
		try {
			os.write("quit\n".getBytes());
			os.flush();
			os.close();
			s.close();
		} catch (Exception ex) {
			// ignor�
		}

		device.removeForward(PORT, PORT);
	}

	public void injectMouse(int action, float x, float y) throws IOException {
		long downTime = 10;
		long eventTime = 10;

		int metaState = -1;

		String cmdList1 = "pointer/" + downTime + "/" + eventTime + "/"
				+ action + "/" + x + "/" + y + "/" + metaState;
		injectData(cmdList1);
	}

	public void injectTrackball(float amount) throws IOException {
		long downTime = 0;
		long eventTime = 0;
		float x = 0;
		float y = amount;
		int metaState = -1;

		String cmdList1 = "trackball/" + downTime + "/" + eventTime + "/"
				+ ConstEvtMotion.ACTION_MOVE + "/" + x + "/" + y + "/"
				+ metaState;
		injectData(cmdList1);
		String cmdList2 = "trackball/" + downTime + "/" + eventTime + "/"
				+ ConstEvtMotion.ACTION_CANCEL + "/" + x + "/" + y + "/"
				+ metaState;
		injectData(cmdList2);
	}

	public void injectKeycode(int type, int keyCode) throws IOException {
		String cmdList = "key/" + type + "/" + keyCode;
		injectData(cmdList);
	}

	private void injectData(String data) throws IOException {
		try {
		os.write((data + "\n").getBytes());
		os.flush();
		} catch(SocketException sex) {
			s = new Socket("127.0.0.1", PORT);
			os = s.getOutputStream();
			os.write((data + "\n").getBytes());
			os.flush();
		}
	}

	private void launchProg(String cmdList) throws IOException {
		String fullCmd = "export CLASSPATH=" + REMOTE_AGENT_JAR_LOCATION;
		fullCmd += "; exec app_process /system/bin " + AGENT_MAIN_CLASS + " "
				+ cmdList;
		System.out.println(fullCmd);
		device.executeShellCommand(fullCmd, new OutputStreamShellOutputReceiver(System.out));
		System.out.println("Prog ended");
	}
}
