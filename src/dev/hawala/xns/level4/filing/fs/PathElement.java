/*
Copyright (c) 2019, Dr. Hans-Walter Latz
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in the
      documentation and/or other materials provided with the distribution.
    * The name of the author may not be used to endorse or promote products
      derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDER "AS IS" AND ANY EXPRESS
OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package dev.hawala.xns.level4.filing.fs;

import java.util.ArrayList;
import java.util.List;

/**
 * Representation of the components (name and version) of a path node
 * in the pathname attribute of a file, including a method to extract the
 * path nodes from a path-text.
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2019)
 */
public class PathElement {
	
	// our special version markers
	public static final int LOWEST_VERSION = -1;     // 0x0000 - 1
	public static final int HIGHEST_VERSION = 65536; // 0xFFFF + 1
	public static final int ANY_VERSION = 65537;     // 0xFFFF + 2
	
	private final String name;
	private final int version;
	
	public PathElement(String name, int version) {
		this.name = name;
		this.version = version;
	}

	public String getName() {
		return name;
	}

	public int getVersion() {
		return version;
	}
	
	public static List<PathElement> parse(String pathString) {
		return parse(pathString, false);
	}
	
	public static List<PathElement> parse(String pathString, boolean noVersionIsAnyVersion) {
		if (pathString == null || pathString.isEmpty()) { return null; }
		while (pathString.startsWith("/")) {
			pathString = pathString.substring(1);
		}
		
		// add special path-string-end marker char
		String ps = pathString + "µ"; // µ is non-ascii and and an xstring-µ should have been recoded away by Dodo's STRING deserializer
		
		StringBuilder sb = new StringBuilder();
		boolean inName = true;
		int len = ps.length();
		
		String elemName = null;
		int defaultVersion = noVersionIsAnyVersion ? ANY_VERSION : HIGHEST_VERSION;
		int elemVersion = defaultVersion;
		
		List<PathElement> path = new ArrayList<PathElement>();
		int i = 0;
		while (i < len) {
			char c = ps.charAt(i++);
			if (c == '\'') { 
				// this should be safe as the path-string-end char (µ) did not show up so far  
				c = ps.charAt(i++);
				sb.append(c);
				continue;
			}
			
			if (c == '/' || c == 'µ') { // path separator or path-string-end
				if (inName) {
					elemName = sb.toString();
					if (elemName.isEmpty()) {
						throw new IllegalArgumentException("Empty path element name");
					}
				} else {
					String vs = sb.toString();
					if (!vs.isEmpty()) { // ignore empty version specs (not filing protocol conformant, but...
						// ... we should be more permissive)
						if (/*"-".equals(vs)*/ vs.startsWith("-")) {
							elemVersion = LOWEST_VERSION;
						} else if (/*"+".equals(vs)*/ vs.startsWith("+")) {
							elemVersion = HIGHEST_VERSION;
						} else {
							int intLen = 0;
							for (int intIdx = 0; intIdx < vs.length(); intIdx++) {
								if (Character.isDigit(vs.charAt(intIdx))) {
									intLen++;
								} else {
									break;
								}
							}
							if (intLen > 0) {
								try {
									elemVersion = Integer.parseInt(vs.substring(0,intLen)) & 0xFFFF;
								} catch (NumberFormatException nfe) { }
							} // else use the default if the version is not numeric at all...
						}
					}
				}
				
				PathElement e = new PathElement(elemName, elemVersion);
				path.add(e);
				
				sb.setLength(0);
				inName = true;
				elemName = null;
				elemVersion = defaultVersion;
			} else if (c == '!') {
				elemName = sb.toString();
				if (elemName.isEmpty()) {
					throw new IllegalArgumentException("Empty path element name");
				}
				sb.setLength(0);
				inName = false;
			} else {
				sb.append(c);
			}
		}
		
		return path;
	}
	
}
