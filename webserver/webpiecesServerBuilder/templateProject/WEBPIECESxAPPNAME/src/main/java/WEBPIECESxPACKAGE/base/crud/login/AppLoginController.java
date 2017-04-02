package WEBPIECESxPACKAGE.base.crud.login;

import javax.inject.Singleton;

import org.webpieces.ctx.api.Current;
import org.webpieces.router.api.actions.Action;
import org.webpieces.router.api.actions.Actions;
import org.webpieces.webserver.api.login.LoginController;

@Singleton
public class AppLoginController extends LoginController {

	@Override
	protected boolean isValidLogin(String username, String password) {
		if(!"dean".equals(username)) {
			Current.flash().setError("No Soup for you!");
			Current.validation().addError("username", "Username must be 'dean'");			
			return false;
		}
		return true;
	}
	
	@Override
	protected Action fetchGetLoginPageAction() {
		return Actions.renderView("/WEBPIECESxPACKAGE/base/crud/login/login.html");
	}

}