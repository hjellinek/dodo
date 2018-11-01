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

package dev.hawala.xns.level4.auth;

import dev.hawala.xns.Log;
import dev.hawala.xns.level1.IDP;
import dev.hawala.xns.level3.courier.CourierRegistry;
import dev.hawala.xns.level3.courier.RECORD;
import dev.hawala.xns.level3.courier.SEQUENCE;
import dev.hawala.xns.level3.courier.UNSPECIFIED;
import dev.hawala.xns.level3.courier.WireWriter;
import dev.hawala.xns.level3.courier.iWireData;
import dev.hawala.xns.level3.courier.iWireStream.EndOfMessageException;
import dev.hawala.xns.level4.auth.Authentication2.CallProblem;
import dev.hawala.xns.level4.auth.Authentication2.CredentialsPackage;
import dev.hawala.xns.level4.auth.Authentication2.Which;
import dev.hawala.xns.level4.common.AuthChsCommon;
import dev.hawala.xns.level4.common.AuthChsCommon.CredentialsType;
import dev.hawala.xns.level4.common.AuthChsCommon.Name;
import dev.hawala.xns.level4.common.AuthChsCommon.NetworkAddress;
import dev.hawala.xns.level4.common.AuthChsCommon.StrongCredentials;
import dev.hawala.xns.level4.common.ChsDatabase;
import dev.hawala.xns.level4.common.StrongAuthUtils;
import dev.hawala.xns.level4.common.Time2;

/**
 * Implementation of the (so far supported) functionality
 * of the Courier Authentication program (PROGRAM 14 VERSION 2).
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2018)
 */
public class Authentication2Impl {
	
	// the clearinghouse database
	private static ChsDatabase chsDatabase = null;
	
	/*
	 * ************************* collection of data to respond (very rudimentary)
	 */
	
	// same defaults as in LocalSite (duplicated to avoid dependency)
	private static long networkId = 0x0001_0120;
	private static long machineId = 0x0000_1000_FF12_3401L;
	
	/**
	 * Set values to use for responses
	 * 
	 * @param network networkId for 'retrieveAddresses'
	 * @param machine hostId for 'retrieveAddresses'
	 */
	public static void init(long network, long machine, ChsDatabase chsDb) {
		networkId = network;
		machineId = machine;
		chsDatabase = chsDb;
	}
	
	/*
	 * ************************* registration/deregistration
	 */
	
	/**
	 * register Courier-Program Clearinghouse3 with this implementation to Courier dispatcher
	 */
	public static void register() {
		Authentication2 prog = new Authentication2();
		
		prog.RetrieveAddresses.use(Authentication2Impl::retrieveAddresses);
		prog.GetStrongCredentials.use(Authentication2Impl::getStrongCredentials);
		prog.CheckSimpleCredentials.use(Authentication2Impl::checkSimpleCredentials);
		// TODO: define remaining procedures in the courier program 
		
		CourierRegistry.register(prog);
	}
	
	/**
	 * unregister Clearinghouse3 implementation from Courier dispatcher
	 */
	public static void unregister() {
		CourierRegistry.unregister(Authentication2.PROGRAM, Authentication2.VERSION);
	}
	
	public static Authentication2 getBfsImplementation() {
		Authentication2 prog = new Authentication2();
		prog.RetrieveAddresses.use(Authentication2Impl::retrieveAddresses);
		return prog;
	}
	
	/*
	 * ************************* implementation of service procedures
	 */
	
	/*
	 * RetrieveAddresses: PROCEDURE
	 * RETURNS [address: NetworkAddressList]
	 * REPORTS [CallError] = 0;
	 */

	public static void retrieveAddresses(
						RECORD params,
						AuthChsCommon.RetrieveAddressesResult results) {
		// add the only known server to the address list
		NetworkAddress addr = results.address.add();
		addr.network.set((int)(networkId & 0xFFFFFFFFL));
		addr.host.set(machineId);
		addr.socket.set(IDP.KnownSocket.COURIER.getSocket()); // unclear what local socket is meant...
	}
	
	/*
	 * GetStrongCredentials: PROCEDURE [
	 *		initiator, recipient: Clearinghouse_Name,
	 *		nonce: LONG CARDINAL ]
	 *	RETURNS [ credentialsPackage: SEQUENCE OF UNSPECIFIED ]
	 * 	REPORTS [ CallError ] = 1;
	 */
	
	public static void getStrongCredentials(
						Authentication2.GetStrongCredentialsParams params,
						Authentication2.GetStrongCredentialsResults results) {
		StringBuilder sb = new StringBuilder();
		String paramsString = params.append(sb, "", "params").toString();
		Log.C.printf("Auth2", "Authentication2Impl.getStrongCredentials(), %s \n", paramsString);
		
		// get the strong passwords
		byte[] strongPwInitiator = getStrongPw(params.initiator, Which.initiator);
		byte[] strongPwRecipient = getStrongPw(params.recipient, Which.recipient);
		Log.C.printf("Auth2", "Authentication2Impl.getStrongCredentials() -- got both strong passwords\n");
		
		// the credentials package built step by step
		CredentialsPackage credPackage = CredentialsPackage.make();
		
		// build the strong Credentials
		int[] conversationKey = StrongAuthUtils.createConversationKey();
		long expiration = Time2.getMesaTime() + 86400L; // now + 1 day
		StrongCredentials creds = StrongCredentials.make();
		for (int i = 0; i < 4; i++) {
			creds.conversationKey.get(i).set(conversationKey[i]);
		}
		creds.expirationTime.set(expiration);
		creds.initiator.object.set(params.initiator.object.get());
		creds.initiator.domain.set(params.initiator.domain.get());
		creds.initiator.organization.set(params.initiator.organization.get());
		logObject(creds, "strong credentials");
		
		// encrypt the strong credentials
		credPackage.credentials.type.set(CredentialsType.strong);
		encryptInto(strongPwRecipient, creds, credPackage.credentials.value);
		
		// proceed with the credentials package
		credPackage.nonce.set(params.nonce.get());
		credPackage.recipient.object.set(params.recipient.object.get());
		credPackage.recipient.domain.set(params.recipient.domain.get());
		credPackage.recipient.organization.set(params.recipient.organization.get());
		for (int i = 0; i < 4; i++) {
			credPackage.conversationKey.get(i).set(conversationKey[i]);
		}
		logObject(credPackage, "credentials package");
		
		// encrypt the credentials package
		encryptInto(strongPwInitiator, credPackage, results.credentialsPackage);
		
		Log.C.printf("Auth2", "Authentication2Impl.getStrongCredentials() -- credentials package encrypted => done\n");
	}
	
	private static void logObject(iWireData data, String prefix) {
		StringBuilder sb = new StringBuilder();
		data.append(sb, "   ", prefix);
		Log.C.printf("Auth2", "Authentication2Impl.getStrongCredentials() ::\n%s \n", sb.toString());
	}
	
	private static byte[] getStrongPw(Name forName, Which which) {
//		// begin temp: pretend there is no strong key to force simple authentication
//		Authentication2.CallErrorRecord err = new Authentication2.CallErrorRecord(
//				CallProblem.strongKeyDoesNotExist,
//				which);
//		Log.C.printf("Auth2", "Authentication2Impl.getStrongPw() -> CallError[strongKeyDoesNotExist,%s]\n", which);
//		err.raise();
//		// end temp
		
		try {
			byte[] pw = chsDatabase.getStrongPassword(forName);
			if (pw != null) {
				return pw;
			}
		} catch (IllegalArgumentException iae) {
			Log.C.printf("Auth2", "Authentication2Impl.getStrongPw() -> CallError[badName,%s]\n", which);
			new Authentication2.CallErrorRecord(CallProblem.badName, which).raise();
		}
		Authentication2.CallErrorRecord callError = new Authentication2.CallErrorRecord(
				CallProblem.strongKeyDoesNotExist,
				which);
		Log.C.printf("Auth2", "Authentication2Impl.getStrongPw() -> CallError[strongKeyDoesNotExist,%s]\n", which);
		callError.raise();
		return null; // keep the compiler happy (cannot know the .raise() does not return...)
	}
	
	private static void encryptInto(byte[] strongPw, iWireData source, SEQUENCE<UNSPECIFIED> target) {
		WireWriter writer = new WireWriter();
		try {
			source.serialize(writer);
			int[] sourceBytes = writer.getWords();
			int[] encrypted = StrongAuthUtils.xnsDesEncrypt(strongPw, sourceBytes);
			for (int i = 0; i < encrypted.length; i++) {
				target.add().set(encrypted[i]);
			}
		} catch (Exception e) {
			// report an "other" error if encrypting fails
			Authentication2.CallErrorRecord err = new Authentication2.CallErrorRecord(
					CallProblem.other,
					Which.notApplicable);
			Log.C.printf("Auth2", "Authentication2Impl.encryptInto() -> CallError[other,notApplicable] :: %s\n", e.getMessage());
			err.raise();
		}
	}

	
	/*
	 * CheckSimpleCredentials: PROCEDURE [credentials: Credentials, verifier: Verifier ]
	 *  RETURNS[ok: BOOLEAN]
	 *  REPORTS[AuthenticationError, CallError] = 2;
	 */
	public static void checkSimpleCredentials(
						Authentication2.CheckSimpleCredentialsParams params,
						Authentication2.CheckSimpleCredentialsResults results) {
		try {
			Log.C.printf("Auth2", "Authentication2Impl.checkSimpleCredentials() -- invoking AuthChsCommon.simpleCheckPasswordForSimpleCredentials()\n");
			results.ok.set(AuthChsCommon.simpleCheckPasswordForSimpleCredentials(chsDatabase, params.credentials, params.verifier));
			Log.C.printf("Auth2", "Authentication2Impl.checkSimpleCredentials() -- result.ok = %s\n", Boolean.toString(results.ok.get()));
		} catch (EndOfMessageException e) {
			Log.C.printf("Auth2", "Authentication2Impl.checkSimpleCredentials() EndOfMessageException when deserializing credsObject -> returning 'false'\n");
			results.ok.set(false);
		} catch (IllegalArgumentException iae) {
			Log.C.printf("Auth2", "Authentication2Impl.checkSimpleCredentials() -> CallError[badName,%s]\n", Which.initiator);
			new Authentication2.CallErrorRecord(CallProblem.badName, Which.initiator).raise();
		}
	}
}
