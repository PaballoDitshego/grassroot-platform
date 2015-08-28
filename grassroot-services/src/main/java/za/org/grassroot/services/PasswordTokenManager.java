package za.org.grassroot.services;

import org.apache.commons.lang.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.Minutes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.VerificationTokenCode;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.core.repository.VerificationTokenCodeRepository;

import java.text.SimpleDateFormat;
import java.util.Random;

/**
 * @author Lesetse Kimwaga
 */
@Service
public class PasswordTokenManager implements PasswordTokenService {

    public static final int TOKEN_LIFE_SPAN_MINUTES = 10;
    private Logger log = LoggerFactory.getLogger(PasswordTokenManager.class);

    private final SimpleDateFormat expirationTimeFormat = new SimpleDateFormat("yyyyMMddHHmm");
    private final int expirationTimeTokenLength = expirationTimeFormat.toPattern().length();

    @Autowired
    private PasswordEncoder passwordTokenEncoder;
    @Autowired
    private VerificationTokenCodeRepository verificationTokenCodeRepository;
    @Autowired
    private UserRepository userRepository;


    @Override
    public VerificationTokenCode generateVerificationCode(User user) {

        VerificationTokenCode verificationTokenCode = verificationTokenCodeRepository.findByUsername(user.getUsername());

        String code = String.valueOf(100000 + new Random().nextInt(999999));

        //String encodedCode = passwordTokenEncoder.encode(code);

        if (verificationTokenCode == null) {
            verificationTokenCode = new VerificationTokenCode(user.getUsername(), code);

        } else {
            verificationTokenCode.setCode(code);
            verificationTokenCode.incrementTokenAttempts();
        }
        verificationTokenCode = verificationTokenCodeRepository.save(verificationTokenCode);

        return verificationTokenCode;
    }

    @Override
    public VerificationTokenCode generateVerificationCode(String username) {

        User user = userRepository.findByUsername(username);

        if (user != null) {
            return generateVerificationCode(user);

        } else {
            return null;
            //throw new InvalidPasswordTokenAccessException("User '" + username + "' does no exist.");
        }

    }

    @Override
    public boolean isVerificationCodeValid(User user, String code) {

        if (user == null || StringUtils.isEmpty(code)) {
            return false;
        }
        //String encodedCode = passwordTokenEncoder.encode(code);
        VerificationTokenCode verificationTokenCode = verificationTokenCodeRepository.findByUsernameAndCode(user.getUsername(), code);

        if (verificationTokenCode == null) {
            return false;
        }
        boolean isGreaterThanLifeSpanMinutes = Minutes.minutesBetween(new DateTime(verificationTokenCode.getCreatedDateTime().getTime()),
                new DateTime())
                .isGreaterThan(Minutes.minutes(TOKEN_LIFE_SPAN_MINUTES));

        return !isGreaterThanLifeSpanMinutes;
    }

    @Override
    public boolean isVerificationCodeValid(String username, String code) {
        return isVerificationCodeValid(userRepository.findByUsername(username),code);
    }

    @Override
    public void invalidateVerificationCode(User user, String code) {

        VerificationTokenCode verificationTokenCode = verificationTokenCodeRepository.findByUsernameAndCode(user.getUsername(), code);
        if (verificationTokenCode != null) {
            verificationTokenCode.setCode(null);
            verificationTokenCodeRepository.save(verificationTokenCode);
        }
    }

}