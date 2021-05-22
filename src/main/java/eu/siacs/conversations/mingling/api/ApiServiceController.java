package eu.siacs.conversations.mingling.api;

import android.text.TextUtils;

import eu.siacs.conversations.mingling.utils.Constants;

import java.security.cert.CertificateException;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

@SuppressWarnings("ALL")
public class ApiServiceController {
    private static boolean DEBUG = true;

    public static <S> S createService(Class<S> serviceClass) {
        return createService(serviceClass, null, null, null);
    }

    public static <S> S createService(Class<S> serviceClass, String serverUrl, String username, String password) {
        if (!TextUtils.isEmpty(username) && !TextUtils.isEmpty(password)) {
            String authToken = Credentials.basic(username, password);
            return createService(serviceClass, serverUrl, authToken);
        } else if (!TextUtils.isEmpty(serverUrl)) {
            return createService(serviceClass, serverUrl, null);
        }

        return createService(serviceClass, null, null);
    }

    public static <S> S createService(Class<S> serviceClass, final String serverUrl, final String authToken) {

        Retrofit.Builder builder = new Retrofit.Builder()
                .addConverterFactory(GsonConverterFactory.create());

        OkHttpClient.Builder httpClient = getOkHttpClient();

        httpClient.readTimeout(Constants.API_CALL_TIMEOUT, TimeUnit.SECONDS);
        httpClient.connectTimeout(Constants.API_CALL_TIMEOUT, TimeUnit.SECONDS);

        if (!TextUtils.isEmpty(authToken)) {
            AuthenticationInterceptor interceptor = new AuthenticationInterceptor(authToken);

            if (!httpClient.interceptors().contains(interceptor)) {
                httpClient.addInterceptor(interceptor);
            }
        }
        if (!TextUtils.isEmpty(serverUrl)) {
            builder.baseUrl(serverUrl);
        }

        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        // set your desired log level
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);
        httpClient.addInterceptor(logging);

        httpClient.setFollowRedirects$okhttp(false);
        httpClient.setFollowSslRedirects$okhttp(false);

        // Custom DNS record check
        // For test
//        httpClient.dns(new Dns() {
//            @NotNull
//            @Override
//            public List<InetAddress> lookup(@NotNull String hostname) throws UnknownHostException {
//                DnsUtils dnsUtils = new DnsUtils();
//                String hostEntry = dnsUtils.getHostByName(hostname);
//                if (!TextUtils.isEmpty(hostEntry)) {
//                    return Arrays.asList(InetAddress.getAllByName(hostEntry));
//                }
//                return Dns.SYSTEM.lookup(hostname);
//            }
//        });

        builder.client(httpClient.build());
        Retrofit retrofit = builder.build();

        return retrofit.create(serviceClass);
    }

    public static OkHttpClient.Builder getOkHttpClient() {
        //return new SSLTrust(SaseApplication.getContext()).getClientBuilder();
        return getUnsafeOkHttpClient();
    }

    /**
     * Skip SSL Handshake
     *
     * @return
     */
    public static OkHttpClient.Builder getUnsafeOkHttpClient() {
        try {
            // Create a trust manager that does not validate certificate chains
            final TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
                        }

                        @Override
                        public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
                        }

                        @Override
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                            return new java.security.cert.X509Certificate[]{};
                        }
                    }
            };

            // Install the all-trusting trust manager
            final SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            // Create an ssl socket factory with our all-trusting manager
            final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

            OkHttpClient.Builder builder = new OkHttpClient.Builder();
            builder.sslSocketFactory(sslSocketFactory, (X509TrustManager) trustAllCerts[0]);
            builder.hostnameVerifier(new HostnameVerifier() {
                @Override
                public boolean verify(String hostname, SSLSession session) {
                    return true;
                }
            });

            return builder;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
