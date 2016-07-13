package org.purplei2p.i2pd;

import java.util.HashSet;
import java.util.Set;

import android.util.Log;

public class DaemonSingleton {
	private static final String TAG="i2pd";
	private static final DaemonSingleton instance = new DaemonSingleton();
	public static interface StateChangeListener { void daemonStateChanged(); }
	private final Set<StateChangeListener> stateChangeListeners = new HashSet<StateChangeListener>();

	public static DaemonSingleton getInstance() {
		return instance;
	}
	
	public synchronized void addStateChangeListener(StateChangeListener listener) { stateChangeListeners.add(listener); }
	public synchronized void removeStateChangeListener(StateChangeListener listener) { stateChangeListeners.remove(listener); }
	
	public void stopAcceptingTunnels() {
		if(isStartedOkay()){
			state=State.gracefulShutdownInProgress;
			fireStateChange();
			I2PD_JNI.stopAcceptingTunnels();
		}
	}
	
	public void onNetworkStateChange(boolean isConnected) {
		I2PD_JNI.onNetworkStateChanged(isConnected);
	}
	
	private boolean startedOkay;

	public static enum State {starting,jniLibraryLoaded,startedOkay,startFailed,gracefulShutdownInProgress};
	
	private State state = State.starting;
	
	public State getState() { return state; }
	
	{
		fireStateChange();
		new Thread(new Runnable(){

			@Override
			public void run() {
				try {
					I2PD_JNI.loadLibraries();
					state = State.jniLibraryLoaded;
					fireStateChange();
				} catch (Throwable tr) {
					lastThrowable=tr;
					state = State.startFailed;
					fireStateChange();
					return;
				}
				try {
					daemonStartResult = I2PD_JNI.startDaemon();
					if("ok".equals(daemonStartResult)){state=State.startedOkay;setStartedOkay(true);}
					else state=State.startFailed;
					fireStateChange();
				} catch (Throwable tr) {
					lastThrowable=tr;
					state = State.startFailed;
					fireStateChange();
					return;
				}				
			}
			
		}, "i2pdDaemonStart").start();
	}
	private Throwable lastThrowable;
	private String daemonStartResult="N/A";

	private synchronized void fireStateChange() {
		Log.i(TAG, "daemon state change: "+state);
		for(StateChangeListener listener : stateChangeListeners) {
			try { 
				listener.daemonStateChanged(); 
			} catch (Throwable tr) { 
				Log.e(TAG, "exception in listener ignored", tr); 
			}
		}
	}

	public Throwable getLastThrowable() {
		return lastThrowable;
	}

	public String getDaemonStartResult() {
		return daemonStartResult;
	}

	public synchronized boolean isStartedOkay() {
		return startedOkay;
	}

	private synchronized void setStartedOkay(boolean startedOkay) {
		this.startedOkay = startedOkay;
	}

	public void stopDaemon() {
		if(isStartedOkay()){
			try {I2PD_JNI.stopDaemon();}catch(Throwable tr){Log.e(TAG, "", tr);}
			setStartedOkay(false);
		}
	}
}
