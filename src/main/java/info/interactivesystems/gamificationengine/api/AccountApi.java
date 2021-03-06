package info.interactivesystems.gamificationengine.api;

import info.interactivesystems.gamificationengine.api.exeption.ApiError;
import info.interactivesystems.gamificationengine.api.exeption.CredentialException;
import info.interactivesystems.gamificationengine.dao.AccountDAO;
import info.interactivesystems.gamificationengine.entities.Account;
import info.interactivesystems.gamificationengine.utils.SecurityTools;

import java.util.Optional;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.validation.constraints.NotNull;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.hibernate.validator.constraints.Email;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.webcohesion.enunciate.metadata.rs.TypeHint;

/**
 * An Account has to be created for at least one manager or developer. It
 * is identified by a unique email address. The password ensures the
 * identity. Optionally an account may have information about the user in form
 * of her/his first name and last name.
 * It is possible to change the password, the first name or last name 
 * at a later point of time. In addition to creating an account another 
 * possible request is to get the created account but only with the 
 * correct credentials.
 * In the response of all requests the hashed password isn't returned because
 *  of security reasons.
 */
@Path("/account")
@Stateless
@Produces(MediaType.APPLICATION_JSON)
public class AccountApi {

	private static final Logger LOGGER = LoggerFactory.getLogger(AccountApi.class);

	@Inject
	AccountDAO accountDao;


	/**
	 * Creates a new account. For this an unique email address and a 
	 * password are mandatory. By the creation of an organisation this 
	 * email address is connected with it. Optionally the first and last
	 * name can also be set.
	 * In the response the password isn't returned because of security 
	 * reasons.
	 *
	 * @param email
	 *            A required valid email address. 
	 * @param password
	 *            Required query parameter to connect it with the given 
	 *            email address.
	 * @param firstName
	 *            Optionally the first name of the Account's owner can be set.
	 * @param lastName
	 *            Optionally the last name of the Account's owner can be set.
	 * @return A Response of Account in JSON.
	 */
	@POST
	@Path("/")
	@TypeHint(Account.class)
	public Response create(@QueryParam("email") @NotNull @Email String email, @QueryParam("password") @NotNull String password,
			@QueryParam("firstName") String firstName, @QueryParam("lastName") String lastName) {

		LOGGER.debug("create account requested");

		if(accountDao.getAccount(email)!=null){
			throw new ApiError(Response.Status.FORBIDDEN, "This mail address is already used.");
		}
		
		Account account = new Account(email);
		account.setPassword(SecurityTools.encryptWithSHA512(password));
		account.setFirstName(firstName);
		account.setLastName(lastName);
		accountDao.persist(account);

		LOGGER.debug("Persisted account: %s", account);
		return ResponseSurrogate.created(account);
	}

	
	/**
	 * Returns an account corresponding to the given email address but only
	 * if the combination with password is correct. By the creation of an 
	 * organisation this email address is connected with it. 
	 * So the method requires valid credentials otherwise a warning with the 
	 * hint for wrong credentials is returned.
	 * In the response the password isn't returned because of security 
	 * reasons.
	 *
	 * @param email
	 *            A required valid unique email address.
	 * @param password
	 *            Required header parameter associated with the email address. 
	 * @return A Response of Account in JSON.
	 */
	@GET
	@Path("/")
	@TypeHint(Account.class)
	public Response get(@QueryParam("email") @NotNull @Email String email, @HeaderParam("password") @NotNull String password) {

		LOGGER.debug("get account requested");

		String encryptedPassword = SecurityTools.encryptWithSHA512(password);
		
		if (!accountDao.checkCredentials(email, encryptedPassword)) {
			LOGGER.warn("Account with wrong credentials (email:\"%s\", pass:\"%s\") requested", email, password);
			throw new CredentialException(email);
		}

		Account account = accountDao.getAccount(email, encryptedPassword);
		LOGGER.debug("Account requested: %s", account);
		return ResponseSurrogate.of(account);
	}
	
	/**
	 * Updates the first and last name of an existing account. For this the 
	 * specific email address and associated password are mandatory.
	 * Otherwise a warning with the hint for wrong credentials is returned.
	 * In the response the password isn't returned because of security 
	 * reasons.
	 *
	 * @param email
	 *            A required valid email address. 
	 * @param password
	 *            Required header parameter to connect it with the given 
	 *            email address.
	 * @param firstName
	 *            Optionally the first name of the Account's owner can be set.
	 * @param lastName
	 *            Optionally the last name of the Account's owner can be set.
	 * 
	 * @return A Response of Account in JSON.
	 */
	@PUT
	@Path("/")
	@TypeHint(Account.class)
	public Response update(@QueryParam("email") @NotNull @Email String email, @HeaderParam("password") @NotNull String password,
			@QueryParam("firstName") String firstName, @QueryParam("lastName") String lastName) {

		LOGGER.debug("update account requested");
		
		String encryptedPassword = SecurityTools.encryptWithSHA512(password);
		if (!accountDao.checkCredentials(email, encryptedPassword)) {
			LOGGER.warn("Account with wrong credentials (email:\"%s\", pass:\"%s\") requested", email, password);
			throw new CredentialException(email);
		}

		Account account = accountDao.getAccount(email, encryptedPassword);
		Optional.ofNullable(encryptedPassword).ifPresent(account::setPassword);
		Optional.ofNullable(firstName).ifPresent(account::setFirstName);
		Optional.ofNullable(lastName).ifPresent(account::setLastName);
		accountDao.persist(account);

		LOGGER.debug("Updated account: %s", account);
		return ResponseSurrogate.updated(account);
	}
}
