/*
Copyright (c) Microsoft Open Technologies, Inc.  All rights reserved.
 
The MIT License (MIT)
 
Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:
 
The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.
 
THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
 */
package com.microsoftopentechnologies.azchat.web.services;

import java.util.Date;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.microsoftopentechnologies.azchat.web.common.exceptions.AzureChatBusinessException;
import com.microsoftopentechnologies.azchat.web.common.exceptions.AzureChatException;
import com.microsoftopentechnologies.azchat.web.common.exceptions.AzureChatSystemException;
import com.microsoftopentechnologies.azchat.web.common.utils.AzureChatConstants;
import com.microsoftopentechnologies.azchat.web.common.utils.AzureChatUtils;
import com.microsoftopentechnologies.azchat.web.dao.FriendRequestDAO;
import com.microsoftopentechnologies.azchat.web.dao.PreferenceMetadataDAO;
import com.microsoftopentechnologies.azchat.web.dao.ProfileImageRequestDAO;
import com.microsoftopentechnologies.azchat.web.dao.UserDAO;
import com.microsoftopentechnologies.azchat.web.dao.UserPreferenceDAO;
import com.microsoftopentechnologies.azchat.web.dao.data.entities.sql.UserEntity;
import com.microsoftopentechnologies.azchat.web.dao.data.entities.sql.UserPreferenceEntity;
import com.microsoftopentechnologies.azchat.web.data.beans.BaseBean;
import com.microsoftopentechnologies.azchat.web.data.beans.UserBean;
import com.microsoftopentechnologies.azchat.web.data.beans.UserPrefBean;

/**
 * Registration service handle user registration tasks by providing operations
 * that take user details as a input and store into the the database.
 * 
 * @author Dnyaneshwar_Pawar
 *
 */
@Service
@Qualifier("registrationService")
public class RegistrationService extends BaseServiceImpl {

	private static final Logger LOGGER = LogManager
			.getLogger(RegistrationService.class);

	@Autowired
	private UserDAO userDao;

	@Autowired
	private ProfileImageRequestDAO profileImageRequestDAO;

	@Autowired
	private FriendRequestDAO friendRequestDAOImpl;

	@Autowired
	private PreferenceMetadataDAO preferenceMetadataDAO;

	@Autowired
	private UserPreferenceDAO userPreferenceDAO;

	@Autowired
	private ContentShareService contentShareService;

	/**
	 * Execute Service implementation for the Registration service.The switch
	 * takes action parameter coming from controller which is instance of
	 * ServiceActionEnum.The actions in this method are mapped conventionally by
	 * ServiceActionEnum to execute the operation and modify the model bean.
	 */
	@Override
	public BaseBean executeService(BaseBean baseBean) throws AzureChatException {
		LOGGER.info("[RegistrationService][executeService] start ");
		BaseBean baseBeanObj = null;
		switch (baseBean.getServiceAction()) {
		case REGISTRATION:
			baseBeanObj = doUserRegistration((UserBean) baseBean);
			break;
		case UPDATE_USER_PROFILE:
			baseBeanObj = updateUserProfile((UserBean) baseBean);
			break;
		default:
			break;
		}
		LOGGER.info("[RegistrationService][executeService] end ");
		return baseBeanObj;
	}

	/**
	 * This method store the user details into the Azure SQL database.
	 * 
	 * @param baseBean
	 * @return
	 * @throws AzureChatSystemException
	 */
	private UserBean doUserRegistration(UserBean userBean)
			throws AzureChatBusinessException, AzureChatSystemException {
		LOGGER.info("[RegistrationService][doUserRegistration] start ");
		LOGGER.debug("User Details    " + userBean.toString());
		if (isNewUser(userBean)) {
			if (AzureChatUtils.isEmptyOrNull(userBean.getNameID())
					|| AzureChatUtils.isEmptyOrNull(userBean.getIdProvider())) {
				throw new AzureChatBusinessException(
						"Unable to process the user registration.Name ID and/or Identity Provider is not set.Please clear browser cookies and try again.");
			}
			UserEntity userEntity = new UserEntity();
			String uri = updateUserProfileImage(userBean);
			try {
				userBean.setPhotoUrl(uri);
				userEntity = buildUserEntity(userBean, userEntity);
				userDao.saveNewUser(userEntity);
				userBean.setNewUser(false);
				// Set user details to carry on home page.
				populateUserBean(userEntity, userBean);
				if (userBean.getUsrPrefList() != null
						&& userBean.getUsrPrefList().size() > 0) {
					for (UserPrefBean userPrefBean : userBean.getUsrPrefList()) {
						if (userPrefBean.getIsChecked()) {
							Integer preferenceMetadataId = preferenceMetadataDAO
									.getPreferenceMetedataIdByDescription(userPrefBean
											.getPrefDesc());
							UserPreferenceEntity userPreferenceEntity = buildUserPreferenceEntity(
									preferenceMetadataId, userBean.getUserID(),
									userPrefBean.getPrefDesc());
							userPreferenceDAO
									.addUserPreferenceEntity(userPreferenceEntity);
						}
					}
				}

			} catch (Exception e) {
				LOGGER.error("Exception occurred while storing the user detail in azure SQL table. Exception Message : "
						+ e.getMessage());
				throw new AzureChatBusinessException(
						"Exception occurred while storing the user detail in azure SQL table. Exception Message : "
								+ e.getMessage());
			}
		}
		LOGGER.info("[RegistrationService][doUserRegistration] end ");
		return userBean;
	}

	/**
	 * This method store the updated user details into the Azure SQL database.
	 * 
	 * @param baseBean
	 * @return
	 */
	private UserBean updateUserProfile(UserBean userBean)
			throws AzureChatBusinessException {
		LOGGER.info("[RegistrationService][updateUserProfile]         start ");
		LOGGER.debug("User Details    " + userBean.toString());
		UserEntity userEntity = new UserEntity();
		String uri = updateUserProfileImage(userBean);
		try {
			if (uri == null) {
				uri = userDao.getUserPhotoBlobURL(Integer.parseInt(userBean
						.getUserID()));
			}
			userBean.setPhotoUrl(uri);
			userEntity = buildUserEntity(userBean, userEntity);
			userDao.updateNewUser(userEntity);
			userBean.setPhotoUrl(uri
					+ AzureChatConstants.CONSTANT_QUESTION_MARK
					+ AzureChatUtils
							.getSASUrl(AzureChatConstants.PROFILE_IMAGE_CONTAINER));
			userBean.setFirstName(userEntity.getFirstName());
			userBean.setLastName(userEntity.getLastName());
		} catch (Exception e) {
			LOGGER.error("Exception occurred while updating user profile details in azure SQL table.Exception Message : "
					+ e.getMessage());
			userBean.setMsg(AzureChatConstants.FAILURE_MSG_USR_PROF_UPDT);
			throw new AzureChatBusinessException(
					"Exception occurred while updating user profile details in azure SQL table.Exception Message : "
							+ e.getMessage());
		}
		userBean.setMsg(AzureChatConstants.SUCCESS_MSG_USR_PROF_UPDT);
		// Set Multipart null to avoid internal server error on JSON parsing
		userBean.setMultipartFile(null);
		LOGGER.info("[RegistrationService][updateUserProfile] end ");
		return userBean;
	}

	/**
	 * populate UserEntity object from UserBean object.
	 * 
	 * @param userBean
	 * @param userEntity
	 * @return
	 */
	private UserEntity buildUserEntity(UserBean userBean, UserEntity userEntity) {
		userEntity.setCreatedBy(new Date());
		userEntity.setDateCreated(new Date());
		userEntity.setDateModified(new Date());
		userEntity.setModifiedBy(new Date());
		userEntity.setEmailAddress(userBean.getEmail() != null ? userBean
				.getEmail() : AzureChatConstants.CONSTANT_EMPTY_STRING);
		userEntity.setFirstName(userBean.getFirstName() != null ? userBean
				.getFirstName() : AzureChatConstants.CONSTANT_EMPTY_STRING);
		userEntity
				.setIdentityProvider(userBean.getIdProvider() != null ? userBean
						.getIdProvider()
						: AzureChatConstants.CONSTANT_EMPTY_STRING);
		userEntity.setLastName(userBean.getLastName() != null ? userBean
				.getLastName() : AzureChatConstants.CONSTANT_EMPTY_STRING);
		userEntity.setNameId(userBean.getNameID() != null ? userBean
				.getNameID() : AzureChatConstants.CONSTANT_EMPTY_STRING);
		userEntity
				.setPhoneCountryCode(userBean.getCountryCD() != null ? Integer
						.parseInt(userBean.getCountryCD()) : 0);
		userEntity.setPhoneNumber(userBean.getPhoneNo() != null ? Long
				.parseLong(userBean.getPhoneNo()) : 0);
		userEntity.setPhotoBlobUrl(userBean.getPhotoUrl() != null ? userBean
				.getPhotoUrl() : AzureChatConstants.CONSTANT_EMPTY_STRING);
		return userEntity;
	}

	/**
	 * This method is a pre-check to avoid duplicate user entries into the SQL
	 * table.
	 * 
	 * @param userBean
	 * @return
	 * @throws AzureChatSystemException
	 */
	private boolean isNewUser(UserBean userBean)
			throws AzureChatSystemException {
		List<UserEntity> userEntities = null;
		boolean isNewUser = true;
		try {
			userEntities = userDao.getUserDetailsByNameIdAndIdentityProvider(
					userBean.getNameID(), userBean.getIdProvider());
			if (null != userEntities && userEntities.size() > 0) {
				isNewUser = false;
				UserEntity user = userEntities.get(0);
				userBean.setNewUser(false);
				populateUserBean(user, userBean);
				// Fetch the content for this user
				userBean = contentShareService.getUserContent(userBean);
				return isNewUser;
			}
		} catch (Exception e) {
			LOGGER.error("Exception occurred while fetching user details from AZURE SQL DB."
					+ e.getMessage());
			throw new AzureChatSystemException(
					"Exception occurred while fetching user details from AZURE SQL DB."
							+ e.getMessage());
		}
		return isNewUser;
	}

	/**
	 * This method stores the user photo URL into azure blob storage.
	 * 
	 * @param userBean
	 * @return
	 * @throws AzureChatBusinessException
	 */
	private String updateUserProfileImage(UserBean userBean)
			throws AzureChatBusinessException {
		LOGGER.info("[RegistrationService][updateUserProfileImage] start");
		String photoURL = null;
		try {
			if (null != userBean.getMultipartFile()
					&& userBean.getMultipartFile().getSize() > 0) {
				photoURL = profileImageRequestDAO.saveProfileImage(
						userBean.getMultipartFile(), userBean.getNameID());
				LOGGER.debug("User Photo URL : " + photoURL);
			}

		} catch (Exception e) {
			LOGGER.error("Exception occurred while storing user profile image in azure blob storage. Exception Message  : "
					+ e.getMessage());
			throw new AzureChatBusinessException(
					"Exception occurred while storing user profile image in azure blob storage. Exception Message  : "
							+ e.getMessage());
		}
		LOGGER.info("[RegistrationService][updateUserProfileImage] end");
		return photoURL;
	}

	/**
	 * This method populates the value from userEntity to the user bean.
	 * 
	 * @param userEntity
	 * @param userBean
	 * @throws Exception
	 */
	private void populateUserBean(UserEntity userEntity, UserBean userBean)
			throws Exception {
		userBean.setUserID(String.valueOf(userEntity.getUserID()));
		userBean.setPendingFriendReq(friendRequestDAOImpl
				.getPendingFriendRequestsCountForUser(userBean.getUserID()));
		userBean.setFirstName(userEntity.getFirstName());
		userBean.setLastName(userEntity.getLastName());
		userBean.setEmail(userEntity.getEmailAddress());
		userBean.setCountryCD(String.valueOf(userEntity.getPhoneCountryCode()));
		userBean.setPhoneNo(String.valueOf(userEntity.getPhoneNumber()));
		userBean.setPhotoUrl(userEntity.getPhotoBlobUrl()
				+ AzureChatConstants.CONSTANT_QUESTION_MARK
				+ AzureChatUtils
						.getSASUrl(AzureChatConstants.PROFILE_IMAGE_CONTAINER));
	}

	/**
	 * This method populates the userPreferenceEntity details.
	 * 
	 * @param preferenceMetadataId
	 * @param userId
	 * @param desc
	 * @return
	 */
	private UserPreferenceEntity buildUserPreferenceEntity(
			Integer preferenceMetadataId, String userId, String desc) {
		UserPreferenceEntity userPreferenceEntity = new UserPreferenceEntity();
		userPreferenceEntity.setPreferenceId(preferenceMetadataId);
		userPreferenceEntity.setUserId(Integer.parseInt(userId));
		userPreferenceEntity.setPreferenceDesc(desc);
		Date date = new Date();
		userPreferenceEntity.setCreatedBy(date);
		userPreferenceEntity.setDateCreated(date);
		userPreferenceEntity.setDateModified(date);
		userPreferenceEntity.setModifiedBy(date);
		return userPreferenceEntity;
	}

}
