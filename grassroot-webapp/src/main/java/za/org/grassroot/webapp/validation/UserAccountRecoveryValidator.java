package za.org.grassroot.webapp.validation;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.ValidationUtils;
import org.springframework.validation.Validator;
import za.org.grassroot.services.PasswordTokenService;
import za.org.grassroot.services.UserManagementService;
import za.org.grassroot.webapp.model.web.UserAccountRecovery;

/**
 * @author Lesetse Kimwaga
 */

@Component("userAccountRecoveryValidator")
public class UserAccountRecoveryValidator implements Validator{

    @Autowired
    PasswordTokenService passwordTokenService;

    @Autowired
    UserManagementService userManagementService;

    @Override
    public boolean supports(Class<?> clazz) {
        return UserAccountRecovery.class.equals(clazz);
    }

    @Override
    public void validate(Object target, Errors errors) {

        ValidationUtils.rejectIfEmpty(errors, "username", "user.account.recovery.username.null");
        ValidationUtils.rejectIfEmpty(errors, "newPassword", "user.account.recovery.newPassword.null");
        ValidationUtils.rejectIfEmpty(errors, "passwordConfirm", "user.account.recovery.passwordConfirm.null");
        ValidationUtils.rejectIfEmpty(errors, "verificationCode", "user.account.recovery.verificationCode.invalid");

        UserAccountRecovery userAccountRecovery = (UserAccountRecovery) target;

        if(!errors.hasErrors())
        {

            if(!userAccountRecovery.getNewPassword().equals(userAccountRecovery.getPasswordConfirm()))
            {
                errors.rejectValue("passwordConfirm","user.account.recovery.passwordConfirm.notEqual");
            }

            if(!passwordTokenService.isVerificationCodeValid(userAccountRecovery.getUsername(),
                    userAccountRecovery.getVerificationCode()))
            {
                errors.rejectValue("verificationCode","user.account.recovery.verificationCode.invalid");
            }

            if(!userManagementService.userExist(userAccountRecovery.getUsername()))
            {
                errors.rejectValue("username", "user.account.recovery.username.nonExistent");
            }
        }
    }
}