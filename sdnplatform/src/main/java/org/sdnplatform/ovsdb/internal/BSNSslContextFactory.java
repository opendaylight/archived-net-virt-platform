/*
 * Copyright (c) 2013 Big Switch Networks, Inc.
 *
 * Licensed under the Eclipse Public License, Version 1.0 (the
 * "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *      http://www.eclipse.org/legal/epl-v10.html
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package org.sdnplatform.ovsdb.internal;

import java.io.InputStream;
import java.security.KeyStore;
import java.security.Security;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;
import java.util.Arrays;

import org.jboss.netty.handler.ssl.SslHandler;

/**
 * Sends client and server certificates and accepts all certificates 
 * presented to us without any verification.
 * 
 * TODO: Use logger for error handling. 
 * TODO: Handle keystore locations.
 * TODO: Error handling if keystore doesn't exists.
 * Maybe use a Singleton instance instead of static initializtion? 
 * 
 * <h3>Client Certificate Authentication</h3>
 *
 * To enable client certificate authentication:
 * <ul>
 * <li>Enable client authentication on the server side by calling
 *     {@link SSLEngine#setNeedClientAuth(boolean)} before creating
 *     {@link SslHandler}.</li>
 * <li>When initializing an {@link SSLContext} on the client side,
 *     specify the {@link KeyManager} that contains the client certificate as
 *     the first argument of {@link SSLContext#init(KeyManager[], javax.net.ssl.TrustManager[], java.security.SecureRandom)}.</li>
 * <li>When initializing an {@link SSLContext} on the server side,
 *     specify the proper {@link TrustManager} as the second argument of
 *     {@link SSLContext#init(KeyManager[], javax.net.ssl.TrustManager[], java.security.SecureRandom)}
 *     to validate the client certificate.</li>
 * </ul>
 */
public class BSNSslContextFactory {

    private static final String PROTOCOL = "SSL";
    private static final SSLContext SERVER_CONTEXT;
    private static final SSLContext CLIENT_CONTEXT;

    static {
        String algorithm = Security.getProperty("ssl.KeyManagerFactory.algorithm");
        if (algorithm == null) {
            algorithm = "SunX509";
        }
        
        /**
         * The following makes sure we do not do any certificate validation when
         * we create a HTTPS connection.
         */

        TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() {
                return null;
            }

            public void checkClientTrusted(X509Certificate[] certs, String authType)
            throws java.security.cert.CertificateException {
            }

            public void checkServerTrusted(X509Certificate[] certs, String authType)
            throws java.security.cert.CertificateException {
            }
        } };

        SSLContext serverContext = null;
        SSLContext clientContext = null;
        //char[] password = "password".toCharArray();
        char[] password = "importkey".toCharArray();
        try {
            KeyStore ks = KeyStore.getInstance("JKS");
            InputStream is = new java.io.FileInputStream("/Users/gregor/work/master/keystore.jks"); 
            ks.load(is, password);
            is.close();

            // Set up key manager factory to use our key store
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(algorithm);
            kmf.init(ks, password);

            // Initialize the SSLContext to work with our key managers.
            // Instead of using trustAllCerts we can also just use null
            serverContext = SSLContext.getInstance(PROTOCOL);
            serverContext.init(kmf.getKeyManagers(), trustAllCerts, null);
            
            clientContext = SSLContext.getInstance(PROTOCOL);
            clientContext.init(kmf.getKeyManagers(), trustAllCerts, null);
        } catch (Exception e) {
            // TODO: error handling is broken!
            throw new Error("Failed to initialize the SSLContexts", e);
        }
        finally {
            Arrays.fill(password, '\0');
        }

        SERVER_CONTEXT = serverContext;
        CLIENT_CONTEXT = clientContext;
    }

    public static SSLContext getServerContext() {
        return SERVER_CONTEXT;
    }

    public static SSLContext getClientContext() {
        return CLIENT_CONTEXT;
    }
}
