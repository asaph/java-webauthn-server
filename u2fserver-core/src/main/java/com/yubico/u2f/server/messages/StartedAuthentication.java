/*
 * Copyright 2014 Yubico.
 * Copyright 2014 Google Inc. All rights reserved.
 *
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file or at
 * https://developers.google.com/open-source/licenses/bsd
 */

package com.yubico.u2f.server.messages;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Objects;
import com.google.gson.Gson;
import com.yubico.u2f.U2fException;
import com.yubico.u2f.codec.RawMessageCodec;
import com.yubico.u2f.key.UserPresenceVerifier;
import com.yubico.u2f.key.messages.AuthenticateResponse;
import com.yubico.u2f.server.ClientDataChecker;
import com.yubico.u2f.server.Crypto;
import com.yubico.u2f.server.data.Device;
import com.yubico.u2f.server.impl.BouncyCastleCrypto;

import java.util.Set;

public class StartedAuthentication {
  /**
   * Version of the protocol that the to-be-registered U2F token must speak. For
   * the version of the protocol described herein, must be "U2F_V2"
   */
  @JsonProperty
  private final String version;

  /** The websafe-base64-encoded challenge. */
  @JsonProperty
  private final String challenge;

  /**
   * The application id that the RP would like to assert. The U2F token will
   * enforce that the key handle provided above is associated with this
   * application id. The browser enforces that the calling origin belongs to the
   * application identified by the application id.
   */
  @JsonProperty
  private final String appId;

  /**
   * websafe-base64 encoding of the key handle obtained from the U2F token
   * during registration.
   */
  @JsonProperty
  private final String keyHandle;

  private final Crypto crypto = new BouncyCastleCrypto();
  private final Set<String> allowedOrigins;

  public StartedAuthentication(String version, String challenge, String appId, String keyHandle, Set<String> origins) {
    this.version = version;
    this.challenge = challenge;
    this.appId = appId;
    this.keyHandle = keyHandle;
    this.allowedOrigins = ClientDataChecker.canonicalizeOrigins(origins);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(version, challenge, appId, keyHandle);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    StartedAuthentication other = (StartedAuthentication) obj;
    if (appId == null) {
      if (other.appId != null)
        return false;
    } else if (!appId.equals(other.appId))
      return false;
    if (challenge == null) {
      if (other.challenge != null)
        return false;
    } else if (!challenge.equals(other.challenge))
      return false;
    if (keyHandle == null) {
      if (other.keyHandle != null)
        return false;
    } else if (!keyHandle.equals(other.keyHandle))
      return false;
    if (version == null) {
      if (other.version != null)
        return false;
    } else if (!version.equals(other.version))
      return false;
    return true;
  }

  public String getKeyHandle() {
    return keyHandle;
  }

  public int finish(TokenAuthenticationResponse tokenResponse, Device device) throws U2fException {
    byte[] clientData = ClientDataChecker.checkClientData(tokenResponse.getClientData(), "navigator.id.getAssertion", challenge, allowedOrigins);

    AuthenticateResponse authenticateResponse = RawMessageCodec.decodeAuthenticateResponse(tokenResponse.getSignatureData());
    byte userPresence = authenticateResponse.getUserPresence();
    if (userPresence != UserPresenceVerifier.USER_PRESENT_FLAG) {
      throw new U2fException("User presence invalid during authentication");
    }

    int counter = authenticateResponse.getCounter();
    if (counter <= device.getCounter()) {
      throw new U2fException("Counter value smaller than expected!");
    }

    byte[] signedBytes = RawMessageCodec.encodeAuthenticateSignedBytes(
            crypto.hash(appId),
            userPresence,
            counter,
            crypto.hash(clientData)
    );
    crypto.checkSignature(
            crypto.decodePublicKey(device.getPublicKey()),
            signedBytes,
            authenticateResponse.getSignature()
    );

    return counter + 1;
  }

  public String json() {
    Gson gson = new Gson();
    return gson.toJson(new AuthenticateRequest(version, challenge, appId, keyHandle));
  }

  public int finish(String tokenResponse, Device device) throws U2fException {
    Gson gson = new Gson();
    return finish(gson.fromJson(tokenResponse, TokenAuthenticationResponse.class), device);
  }

  public String getChallenge() {
    return challenge;
  }

  public String getAppId() {

    return appId;
  }

  private static class AuthenticateRequest {
    private final String version;
    private final String challenge;
    private final String appId;
    private final String keyHandle;

    private AuthenticateRequest(String version, String challenge, String appId, String keyHandle) {
      this.version = version;
      this.challenge = challenge;
      this.appId = appId;
      this.keyHandle = keyHandle;
    }
  }
}
