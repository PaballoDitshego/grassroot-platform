package za.org.grassroot.services;

import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.enums.GroupDefaultImage;

/**
 * Created by luke on 2016/09/26.
 */
public interface GroupImageBroker {

    void setGroupImageToDefault(String userUid, String groupUid, GroupDefaultImage defaultImage, boolean removeCustomImage);

    void saveGroupImage(String userUid, String groupUid, String format, byte[] image);

    Group getGroupByImageUrl(String imageUrl);

}
