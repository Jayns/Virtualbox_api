import java.io.*;
import java.util.*;
import java.lang.String;

//FOR VIRTUALBOX
import org.virtualbox_6_1.*;


//This class is written in java, it does not has any groovy specific keywords.
public class VMCommons {
	private final VirtualBoxManager vbManager;
	private final IVirtualBox vbox;
	private IProgress progress;

	public VMCommons(String IP) {
		vbManager = VirtualBoxManager.createInstance(null);
		vbManager.connect(IP, null, null);
		vbox = vbManager.getVBox();
	}

	public boolean machineExists(String machineName) {
		if (machineName == null) {
			System.err.println("Machine Exists method machine name is null.");
			return false;
		}
		List<IMachine> machines = vbox.getMachines();
		for (IMachine machine : machines) {
			if (machine.getName().equals(machineName)) {
				return true;
			}
		}
		return false;
	}

	public boolean snapshotExists(IMachine machine, String snapshot) {
		return machine.findSnapshot(snapshot) != null;
	}

	public String getMachineIPv4(String machineName) {
		if (!machineExists(machineName)) {
			return null;
		}
		IMachine machine = vbox.findMachine(machineName);
		Holder<List<String>> keys = new Holder<>();
		Holder<List<String>> values = new Holder<>();
		Holder<List<Long>> timestamps = new Holder<>();
		Holder<List<String>> flags = new Holder<>();
		machine.enumerateGuestProperties(null, keys, values, timestamps, flags);
		String ipv4 = null;
		for (int i = 0; i < keys.value.size(); i++) {
			String key = keys.value.get(i);
			String val = values.value.get(i);
			if (key.contains("GuestInfo/Net/0/V4/IP") && val.startsWith("10.0")) {
				ipv4 = val;
				break;
			}
		}
		return ipv4;
	}

	public boolean launchMachine(String machineName, String mode) {
		if (!machineExists(machineName)) {
			return false;
		}
		IMachine machine = vbox.findMachine(machineName);
		ISession session = vbManager.getSessionObject();
		SessionState sessionState = machine.getSessionState();
		IProgress progress = machine.launchVMProcess(session, mode, null);
		progress.waitForCompletion(2500);
		try {
			session.unlockMachine();
		}catch(Exception e) {
			System.err.println("Error occured during unlock machine. Err: " + e.toString());
		}
		while(SessionState.Spawning.equals(sessionState)) {
			System.out.println("Session state is in Spawning. Waiting 1 sec.");
			Thread.sleep(1000);
			sessionState = machine.getSessionState();
		}
		try {
			//WARNING IP number shall not be acquired if the target computer has no network.
			String ipv4 = null;
			Thread.sleep(3000);
			ipv4 = getMachineIPv4(machineName);
			int counter = 0;
			while(ipv4 == null && counter < 20) {
				System.err.println("IP Adress is null. Trying again. Trials left : " + (20 - counter));
				Thread.sleep(3000);
				ipv4 = getMachineIPv4(machineName);
				counter++;
			}
			if(ipv4 != null) {
				System.out.println("IP Acquired for : " + machineName + ", IP : " + ipv4);
			}
			else {
				System.err.println("No IP Acquired for : " + machineName + ", Any interaction that requires network activity will fail. Check internet connection of the vm. Cont...");
			}
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		return true;
	}

	public void shutdownMachine(String machineName) {
		if (!machineExists(machineName)) {
			System.err.println("Machine does not exists.")
			return;
		}
		IMachine machine = vbox.findMachine(machineName);
		MachineState state = machine.getState();
		ISession session = vbManager.getSessionObject();
		machine.lockMachine(session, LockType.Shared);
		try {
			if (state.value() >= MachineState.FirstOnline.value() && state.value() <= MachineState.LastOnline.value()) {
				IProgress progress = session.getConsole().powerDown();
				progress.waitForCompletion(25000);
			}
		} finally {
			waitToUnlock(session, machine);
		}
	}

	public void waitToUnlock(ISession session, IMachine machine) {
		session.unlockMachine();
		SessionState sessionState = machine.getSessionState();
		while (!SessionState.Unlocked.equals(sessionState)) {
			sessionState = machine.getSessionState();
			try {
				System.err.println("Waiting for session unlock...[" + sessionState.name() + "][" + machine.getName() + "]");
				Thread.sleep(1000L);
			} catch (InterruptedException e) {
				System.err.println("Interrupted while waiting for session to be unlocked");
			}
		}
	}

	public boolean restoreSnapshot(String machineName, String snapshotName) {
		IMachine machine = vbox.findMachine(machineName);
		ISession session = vbManager.getSessionObject();
		machine.lockMachine(session, LockType.Shared);

		ISnapshot snapshot = machine.findSnapshot(snapshotName);

		IProgress progress = session.getMachine().restoreSnapshot(snapshot);
		progress.waitForCompletion(25000);
		try {
			waitToUnlock(session, machine);
		}catch(Exception e) {
			System.out.println(e.toString());
			return false;
		}
		return true;
	}

	public boolean isMachineRunning(String machineName) {
		IMachine machine = vbox.findMachine(machineName);
		MachineState state = machine.getState();

		System.out.println("Status of Machine : " + machineName + "is : " + state);

		if(state == MachineState.Running) {
			return true;
		}
		return false;
	}
}