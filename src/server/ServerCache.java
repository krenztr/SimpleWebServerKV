/*
 * ServerCache.java
 * Oct 27, 2012
 *
 * Simple Web Server (SWS) for EE407/507 and CS455/555
 * 
 * Copyright (C) 2011 Chandan Raj Rupakheti, Clarkson University
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
 * Contact Us:
 * Chandan Raj Rupakheti (rupakhcr@clarkson.edu)
 * Department of Electrical and Computer Engineering
 * Clarkson University
 * Potsdam
 * NY 13699-5722
 * http://clarkson.edu/~rupakhcr
 */
 
package server;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;

/**
 * A cache of files from the server. By reading frequently requested files from memory rather than the hard drive, performance will increase. Uses LRU algorithm.
 * @author Trevor Krenz
 */
public class ServerCache {
	private int maxNumFiles;
	private LinkedHashMap<String, byte[]> cache;
	
	public ServerCache(int maxNumFiles){
		this.maxNumFiles = maxNumFiles;
		this.cache = new LinkedHashMap<String, byte[]>();
	}
	public InputStream get(File file){
		String path = file.getAbsolutePath();
		byte[] data = cache.get(path);
		if(data != null){
			//Cache hit...
			//Move the file to the end of the queue
			cache.remove(path);
			cache.put(path,data);
			//Return the input stream of the data
			return new ByteArrayInputStream(data);
		}
		//Cache miss...
		try {
			FileInputStream fileInStream = new FileInputStream(file);
			data = new byte[(int) file.length()];
			fileInStream.read(data);
			if(cache.size() >= maxNumFiles)
			{
				//Remove the file at head of queue
				cache.remove(cache.keySet().iterator().next());
			}
			//Put new file in end of queue
			cache.put(path, data);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return new ByteArrayInputStream(data);
	}
}
