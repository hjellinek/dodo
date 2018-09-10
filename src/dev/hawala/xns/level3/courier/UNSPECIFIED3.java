/*
Copyright (c) 2018, Dr. Hans-Walter Latz
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

package dev.hawala.xns.level3.courier;

import dev.hawala.xns.level3.courier.iWireStream.EndOfMessageException;
import dev.hawala.xns.level3.courier.iWireStream.NoMoreWriteSpaceException;

/**
 * Representation of the Courier UNSPECIFIED3 datatype (3 words).
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2018)
 */
public class UNSPECIFIED3 implements iWireData {

	private long value = 0;
	
	UNSPECIFIED3() { }

	public UNSPECIFIED3 set(long val) {
		this.value = val & 0x0000_FFFF_FFFF_FFFFL;
		return this;
	}
	
	public long get() {
		return this.value;
	}

	@Override
	public void serialize(iWireStream ws) throws NoMoreWriteSpaceException {
		ws.writeI48(this.value);
	}

	@Override
	public void deserialize(iWireStream ws) throws EndOfMessageException {
		this.value = ws.readI48() & 0x0000_FFFF_FFFF_FFFFL;
	}
	
	@Override
	public StringBuilder append(StringBuilder to, String indent, String fieldName) {
		to.append(indent).append(fieldName).append(": ").append(Long.toString(this.value & 0xFFFF_FFFF_FFFFL));
		return to;
	}
	
	public static UNSPECIFIED3 create() { return new UNSPECIFIED3(); }
	
}
