/*
 * Server.java
 * Oct 7, 2012
 *
 * Simple Web Server (SWS) for CSSE 477
 * 
 * Copyright (C) 2012 Chandan Raj Rupakheti
 * 
 * This program is free software: you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License 
 * as published by the Free Software Foundation, either 
 * version 3 of the License, or any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/lgpl.html>.
 * 
 */

package server;

import gui.WebServer;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map.Entry;

/**
 * This represents a welcoming server for the incoming TCP request from a HTTP
 * client such as a web browser.
 * 
 * @author Chandan R. Rupakheti (rupakhet@rose-hulman.edu)
 */
public class Server implements Runnable {
	private class BlacklistTimer extends Thread {
		private HashMap<String, Long> map;
		private boolean loop;

		public BlacklistTimer(HashMap<String, Long> map) {
			this.loop = true;
			this.map = map;

		}

		public void run() {
			while (loop) {
				try {
					sleep(BLACKLIST_THREAD_INTERVAL);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				Iterator<Entry<String, Long>> i = map.entrySet().iterator();
				while (i.hasNext()) {
					Entry<String, Long> ip = i.next();
					if (ip.getValue() < System.currentTimeMillis()) {
						this.map.remove(ip.getKey());
					}
				}
			}
		}

		public void stopLoop() {
			this.loop = false;
		}
	}

	private String rootDirectory;
	private int port;
	private boolean stop;
	private ServerSocket welcomeSocket;
	private ServerCache serverCache;

	private long connections;
	private long serviceTime;

	private WebServer window;

	private HashSet<String> forbiddenPages;
	private HashSet<String> hiddenPages;
	private long lastSecond;

	private HashMap<InetAddress, Integer> numRequest;
	private static final int REQUESTS_PER_SECOND_THREHOLD = 100;
	private HashMap<String, Long> blacklist;
	private static final int BLACKLIST_TIME = 900000;
	private static final int BLACKLIST_THREAD_INTERVAL = 600000;

	private static final int MAX_CACHED_FILES = 20;
	private File log;

	/**
	 * @param rootDirectory
	 * @param port
	 */
	public Server(String rootDirectory, int port, WebServer window) {
		this.rootDirectory = rootDirectory;
		this.port = port;
		this.stop = false;
		this.connections = 0;
		this.serviceTime = 0;
		this.window = window;
		this.serverCache = new ServerCache(MAX_CACHED_FILES);
		this.forbiddenPages = new HashSet<String>();
		this.hiddenPages = new HashSet<String>();
		this.numRequest = new HashMap<InetAddress, Integer>();
		this.blacklist = new HashMap<String, Long>();
		// Try to add "forbidden.html" to forbidden files just as an example.
		File f = new File(rootDirectory + "/forbidden.html");
		if (f.exists()) {
			forbiddenPages.add(f.getAbsolutePath());
		}
		// Try to add "hidden.html" to hidden files just as an example.
		f = new File(rootDirectory + "/hidden.html");
		if (f.exists()) {
			hiddenPages.add(f.getAbsolutePath());
		}
		//Make the log
		this.log = new File("serverLog.log");
		if (!this.log.exists())
			try {
				this.log.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
			}
	}

	public HashSet<String> getForbiddenPages() {
		return this.forbiddenPages;
	}
	
	public HashSet<String> getHiddenPages() {
		return this.hiddenPages;
	}

	/**
	 * Gets the root directory for this web server.
	 * 
	 * @return the rootDirectory
	 */
	public String getRootDirectory() {
		return rootDirectory;
	}

	/**
	 * Gets the port number for this web server.
	 * 
	 * @return the port
	 */
	public int getPort() {
		return port;
	}

	/**
	 * Returns connections serviced per second. Synchronized to be used in
	 * threaded environment.
	 * 
	 * @return
	 */
	public synchronized double getServiceRate() {
		if (this.serviceTime == 0)
			return Long.MIN_VALUE;
		double rate = this.connections / (double) this.serviceTime;
		rate = rate * 1000;
		return rate;
	}

	/**
	 * Increments number of connection by the supplied value. Synchronized to be
	 * used in threaded environment.
	 * 
	 * @param value
	 */
	public synchronized void incrementConnections(long value) {
		this.connections += value;
	}

	/**
	 * Increments the service time by the supplied value. Synchronized to be
	 * used in threaded environment.
	 * 
	 * @param value
	 */
	public synchronized void incrementServiceTime(long value) {
		this.serviceTime += value;
	}

	/**
	 * The entry method for the main server thread that accepts incoming TCP
	 * connection request and creates a {@link ConnectionHandler} for the
	 * request.
	 */
	public void run() {
		// SecurityManager s = new SimpleSecurityManager(blacklist);
		// System.setSecurityManager(s);
		try {
			this.welcomeSocket = new ServerSocket(port);
			BlacklistTimer blacklistTimer = new BlacklistTimer(this.blacklist);
			blacklistTimer.start();

			// Now keep welcoming new connections until stop flag is set to true
			while (true) {
				// Listen for incoming socket connection
				// This method block until somebody makes a request
				Socket connectionSocket = this.welcomeSocket.accept();
				if (DOSCheck(connectionSocket)) {
					connectionSocket.close();
				} else {
					// System.out.println("Address: "
					// + connectionSocket.getInetAddress());

					// Come out of the loop if the stop flag is set
					if (this.stop)
						break;

					// Create a handler for this incoming connection and start
					// the handler in a new thread
					ConnectionHandler handler = new ConnectionHandler(this,
							connectionSocket, serverCache);
					new Thread(handler).start();
				}
			}
			this.welcomeSocket.close();
			blacklistTimer.stopLoop();
		} catch (Exception e) {
			FileWriter fstream;
			try {
				fstream = new FileWriter(this.log.getAbsolutePath());
				BufferedWriter out = new BufferedWriter(fstream);
				out.write(e.toString());
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		}
	}

	public boolean DOSCheck(Socket connectionSocket) {
		// Iterator it = blacklist.iterator();
		// System.out.println("Blacklist: ");
		// while (it.hasNext()) {
		// System.out.println(it.next());
		// }
		// Iterator it2 = numRequest.entrySet().iterator();
		// System.out.println("NumRequest: ");
		// while (it2.hasNext()) {
		// System.out.println(it2.next());
		// }
		if (blacklist.containsKey(connectionSocket.getInetAddress()))
			return true;
		// Clear the request counter every second
		long currentSecond = System.currentTimeMillis() / 1000L;
		if (currentSecond > lastSecond) {
			numRequest.clear();
			lastSecond = currentSecond;
		}
		InetAddress ip = connectionSocket.getInetAddress();
		// Increment request counter
		if (numRequest.containsKey(ip)) {
			Integer i = numRequest.get(ip) + 1;
			numRequest.put(ip, i);
			// If requests go over, put on blacklist
			if (i >= REQUESTS_PER_SECOND_THREHOLD) {
				blacklist.put(ip.getHostAddress(), System.currentTimeMillis()
						+ BLACKLIST_TIME);
				return true;
			}
		} else {
			numRequest.put(ip, 1);
		}
		return false;
	}

	/**
	 * Stops the server from listening further.
	 */
	public synchronized void stop() {
		if (this.stop)
			return;

		// Set the stop flag to be true
		this.stop = true;
		try {
			// This will force welcomeSocket to come out of the blocked accept()
			// method
			// in the main loop of the start() method
			Socket socket = new Socket(InetAddress.getLocalHost(), port);

			// We do not have any other job for this socket so just close it
			socket.close();
		} catch (Exception e) {
			FileWriter fstream;
			try {
				fstream = new FileWriter(this.log.getAbsolutePath());
				BufferedWriter out = new BufferedWriter(fstream);
				out.write(e.toString());
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		}
	}

	/**
	 * Checks if the server is stopeed or not.
	 * 
	 * @return
	 */
	public boolean isStoped() {
		if (this.welcomeSocket != null)
			return this.welcomeSocket.isClosed();
		return true;
	}
}
