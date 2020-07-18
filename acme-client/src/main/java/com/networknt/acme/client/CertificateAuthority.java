package com.networknt.acme.client;

import java.io.FileWriter;
import java.io.IOException;
import java.security.KeyPair;

import org.shredzone.acme4j.Account;
import org.shredzone.acme4j.Authorization;
import org.shredzone.acme4j.Certificate;
import org.shredzone.acme4j.Order;
import org.shredzone.acme4j.Session;
import org.shredzone.acme4j.Status;
import org.shredzone.acme4j.challenge.Http01Challenge;
import org.shredzone.acme4j.exception.AcmeException;
import org.shredzone.acme4j.util.CSRBuilder;

import com.networknt.acme.client.persistance.CertificateStore;
import com.networknt.acme.client.persistance.FileCertificateStore;
import com.networknt.acme.client.persistance.FileKeyStore;
import com.networknt.config.Config;

public class CertificateAuthority {
	private static final String BASE_PATH = "user.home";
	private static final String CERTFICATE_SIGNING_REQUEST_FILE = "/example.csr";
	private static final String CERTIFICATE_FILE = "/certificate.pem";
	private static final String DOMAIN_KEY_FILE = "/domain.key";
	private static ACMEConfig config = (ACMEConfig) Config.getInstance().getJsonObjectConfig("acme", ACMEConfig.class);

	public Certificate order() throws AcmeException, InterruptedException, IOException {
		Session session = new SessionFactory().getPebbleSession();
		Account account = new AccountManager().getAccount(session);
		Order order = orderCertificate(account);
		storeCertificate(order);
		return order.getCertificate();
	}

	private Order orderCertificate(Account account) throws AcmeException, InterruptedException, IOException {
		Order order = createOrder(account);
		byte[] csr = createCSR();
		order.execute(csr);
		while (order.getStatus() != Status.VALID) {
			Thread.sleep(3000L);
			order.update();
		}
		return order;
	}

	private Order createOrder(Account account) throws AcmeException, InterruptedException, IOException {
		String domain = config.getDomain();
		Order order = account.newOrder().domains(domain).create();
		for (Authorization auth : order.getAuthorizations()) {
			if (auth.getStatus() != Status.VALID) {
				processAuth(auth);
			}
		}
		return order;
	}

	private void processAuth(Authorization auth) throws AcmeException, InterruptedException, IOException {
		Http01Challenge challenge = auth.findChallenge(Http01Challenge.class);
		HTTPChallengeResponder responder = new HTTPChallengeResponder(challenge.getAuthorization());
		responder.start();
		challenge.trigger();
		while (auth.getStatus() != Status.VALID) {
			Thread.sleep(1000L);
			auth.update();
		}
		responder.stop();
	}

	private byte[] createCSR() throws IOException {
		KeyPair domainKeyPair = new FileKeyStore().getKey(System.getProperty(BASE_PATH) + DOMAIN_KEY_FILE);
		CSRBuilder csrb = new CSRBuilder();
		csrb.addDomain("test.com");
		csrb.sign(domainKeyPair);
		csrb.write(new FileWriter(System.getProperty(BASE_PATH) + CERTFICATE_SIGNING_REQUEST_FILE));
		return csrb.getEncoded();
	}

	private void storeCertificate(Order order) {
		CertificateStore certStore = new FileCertificateStore();
		certStore.store(order.getCertificate(), System.getProperty(BASE_PATH) + CERTIFICATE_FILE);
	}

}
