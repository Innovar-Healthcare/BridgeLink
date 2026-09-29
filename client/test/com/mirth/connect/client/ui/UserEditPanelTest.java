package com.mirth.connect.client.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.awt.GraphicsEnvironment;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

import com.mirth.connect.model.User;

public class UserEditPanelTest {

	private static final List<Locale> NON_ENGLISH_LOCALES = Arrays.asList(Locale.GERMANY, Locale.JAPAN, Locale.FRANCE);

	private static final UserDialogInterface STUB_DIALOG = new UserDialogInterface() {
		@Override
		public void setFinishButtonEnabled(boolean enabled) {}

		@Override
		public void triggerFinishButton() {}
	};

	/**
	 * The panel's text fields are MirthTextFields, which dereference PlatformUI.MIRTH_FRAME as soon
	 * as their text is set.
	 */
	@BeforeClass
	public static void setUpFrame() {
		if (!GraphicsEnvironment.isHeadless()) {
			PlatformUI.MIRTH_FRAME = Mockito.mock(Frame.class);
		}
	}

	@AfterClass
	public static void tearDownFrame() {
		PlatformUI.MIRTH_FRAME = null;
	}

	/**
	 * The country names are pinned to English so the value persisted on the user does not depend on
	 * the locale of the machine running the Administrator.
	 */
	@Test
	public void testCountryNamesArePinnedToEnglish() {
		Locale originalLocale = Locale.getDefault();
		try {
			for (Locale locale : NON_ENGLISH_LOCALES) {
				Locale.setDefault(locale);
				assertEquals("United States", UserEditPanel.buildCountryMap().get("US"));
			}
		} finally {
			Locale.setDefault(originalLocale);
		}
	}

	/**
	 * Duplicate display names would put duplicate entries in the dropdown and make the reverse
	 * lookup from country name to country code ambiguous.
	 */
	@Test
	public void testCountryDisplayNamesAreUnique() {
		Map<String, String> countryMap = UserEditPanel.buildCountryMap();
		assertEquals(countryMap.size(), new HashSet<String>(countryMap.values()).size());
	}

	/**
	 * IRT-1658: editing a user threw a NullPointerException before the dialog opened on any machine
	 * whose default locale was not English.
	 */
	@Test
	public void testEditUserUnderNonEnglishLocale() {
		Assume.assumeFalse(GraphicsEnvironment.isHeadless());

		Locale originalLocale = Locale.getDefault();
		try {
			for (Locale locale : NON_ENGLISH_LOCALES) {
				Locale.setDefault(locale);

				User user = new User();
				user.setUsername("testuser");
				user.setCountry("United States");

				UserEditPanel panel = new UserEditPanel();
				panel.setUser(STUB_DIALOG, user);

				assertEquals(locale.toString(), "US", panel.getSelectedCountryCode());
				assertEquals(locale.toString(), "United States", panel.getUser().getCountry());
			}
		} finally {
			Locale.setDefault(originalLocale);
		}
	}

	/**
	 * A country name persisted by a client running in another locale is not in the dropdown. The
	 * panel keeps its default selection rather than leaving the combo box in an invalid state.
	 */
	@Test
	public void testLegacyLocalizedCountryValueDoesNotCrash() {
		Assume.assumeFalse(GraphicsEnvironment.isHeadless());

		User user = new User();
		user.setUsername("testuser");
		user.setCountry("Vereinigte Staaten");

		UserEditPanel panel = new UserEditPanel();
		panel.setUser(STUB_DIALOG, user);

		assertEquals("US", panel.getSelectedCountryCode());
	}

	/**
	 * A country stored as an ISO code resolves to the matching dropdown entry.
	 */
	@Test
	public void testCountryCodeResolvesToCountryName() {
		Assume.assumeFalse(GraphicsEnvironment.isHeadless());

		User user = new User();
		user.setUsername("testuser");
		user.setCountry("au");

		UserEditPanel panel = new UserEditPanel();
		panel.setUser(STUB_DIALOG, user);

		assertEquals("AU", panel.getSelectedCountryCode());
		assertEquals("Australia", panel.getUser().getCountry());
	}

	@Test
	public void testValidatePhoneNumber() {
		// Valid US phone numbers
		assertTrue(UserEditPanel.validatePhoneNumber("(714) 555-5555", "US"));
		assertTrue(UserEditPanel.validatePhoneNumber("1 (714) 555-5555", "US"));
		assertTrue(UserEditPanel.validatePhoneNumber("7145555555", "US"));
		assertTrue(UserEditPanel.validatePhoneNumber("714-555-5555", "US"));
		
		// Invalid US phone numbers
		assertFalse(UserEditPanel.validatePhoneNumber("(714) 555-555", "US"));	// Too few digits
		assertFalse(UserEditPanel.validatePhoneNumber("71455555555", "US"));	// Too many digits
		assertFalse(UserEditPanel.validatePhoneNumber("555-5555", "US"));		// No area code
		assertFalse(UserEditPanel.validatePhoneNumber("555-555-5555", "US"));	// Non-existent area code
		
		// Valid AU phone numbers
		assertTrue(UserEditPanel.validatePhoneNumber("0455 555 555", "AU"));
		assertTrue(UserEditPanel.validatePhoneNumber("0455555555", "AU"));
		assertTrue(UserEditPanel.validatePhoneNumber("455 555 555", "AU"));
		assertTrue(UserEditPanel.validatePhoneNumber("455555555", "AU"));
		
		// Invalid AU phone numbers
		assertFalse(UserEditPanel.validatePhoneNumber("0455 555 55", "AU"));		// Too few digits
		assertFalse(UserEditPanel.validatePhoneNumber("04555555555", "AU"));		// Too many digits
		assertFalse(UserEditPanel.validatePhoneNumber("00455 555 555", "AU"));	// Too many leading zeroes
	}
		
}
