package com.university.docassistant.config;

import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * TLS trust for outgoing HTTPS calls: the JDK's own CA certificates plus, on Windows, the Windows certificate store.
 * Antivirus HTTPS scanning (e.g. Avast Web Shield) and corporate proxies re-sign traffic with a root that is
 * installed in Windows but not in the JDK, so without this every call fails with "unable to find valid
 * certification path" although browsers and curl work. Certificates are still fully verified; only the set of
 * trusted roots is larger.
 */
@Slf4j
public final class SystemTrust {

    private SystemTrust() {
    }

    public static SSLContext sslContext() {
        try {
            List<X509TrustManager> managers = new ArrayList<>();
            managers.add(trustManager(null));
            if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows")) {
                try {
                    KeyStore windowsRoots = KeyStore.getInstance("Windows-ROOT");
                    windowsRoots.load(null, null);
                    managers.add(trustManager(windowsRoots));
                } catch (GeneralSecurityException | java.io.IOException ex) {
                    log.warn("Windows certificate store is not available, using JDK CAs only: {}", ex.toString());
                }
            }
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, new TrustManager[]{new AnyOf(managers)}, null);
            return context;
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Cannot initialize TLS", ex);
        }
    }

    private static X509TrustManager trustManager(KeyStore store) throws GeneralSecurityException {
        TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init(store);
        for (TrustManager manager : factory.getTrustManagers()) {
            if (manager instanceof X509TrustManager x509) {
                return x509;
            }
        }
        throw new GeneralSecurityException("No X509TrustManager");
    }

    /** Trusts a server chain that any of the managers trusts. */
    private record AnyOf(List<X509TrustManager> managers) implements X509TrustManager {

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            CertificateException last = null;
            for (X509TrustManager manager : managers) {
                try {
                    manager.checkServerTrusted(chain, authType);
                    return;
                } catch (CertificateException ex) {
                    last = ex;
                }
            }
            throw last;
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            managers.get(0).checkClientTrusted(chain, authType);
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return managers.stream().flatMap(manager -> java.util.Arrays.stream(manager.getAcceptedIssuers()))
                    .toArray(X509Certificate[]::new);
        }
    }
}
