package com.deelabs.untisplus.ui.pages.login.sso

/**
 * The single-sign-on identity provider a school's WebUntis deployment advertises.
 * Mirrors the providers Untis itself detects on its web login page (Office365 / Google / OIDC).
 *
 * [label] carries the human-readable provider name for the login button (e.g. "Bildungsportal"),
 * [loginUrlPath] is the relative Untis entry used to start the IdP flow (OIDC has no LoginUrl
 * field in the config — it is always `oidc/login`).
 */
class SsoProvider(
	val type: Type,
	val label: String,
	val loginUrlPath: String
) {
	enum class Type {
		OFFICE_365,
		GOOGLE,
		OIDC,
		UNKNOWN
	}

	companion object {
		private const val OIDC_LOGIN_PATH = "oidc/login"

		/** Placeholder used before async detection completes; renders a generic SSO button. */
		val Unknown: SsoProvider = SsoProvider(Type.UNKNOWN, "", "")

		/** Parses a school's landing-page bootstrap config into the advertised SSO provider, or null. */
		fun fromConfig(html: String): SsoProvider? {
			val loginUrls = extractLoginUrls(html)

			// Office365/Google expose an explicit LoginUrl in the config.
			val explicit = loginUrls.minByOrNull { rel ->
				when {
					rel.contains("office", true) || rel.contains("o365", true) -> 0
					rel.contains("google", true) -> 1
					rel.contains("azure", true) -> 2
					rel.contains("oidc", true) || rel.contains("openid", true) -> 3
					else -> 4
				}
			}
			if (explicit != null) {
				val type = when {
					explicit.contains("office", true) || explicit.contains("o365", true) ||
						explicit.contains("azure", true) -> Type.OFFICE_365
					explicit.contains("google", true) -> Type.GOOGLE
					else -> Type.OIDC
				}
				return SsoProvider(type, oidcLabel(html), explicit)
			}

			// OIDC can be configured without a LoginUrl — only the flag + a display label.
			if (containsTrue("ssoOidc", html)) {
				return SsoProvider(Type.OIDC, oidcLabel(html), OIDC_LOGIN_PATH)
			}

			return null
		}

		/** The display label the web login page shows for OIDC, taken verbatim from the page config. */
		private fun oidcLabel(html: String): String {
			val m = Regex("\"oidcProviderLabel\"\\s*:\\s*\"([^\"]*)\"").find(html)
			return m?.groupValues?.get(1)?.takeIf { it.isNotBlank() } ?: ""
		}

		private fun containsTrue(flag: String, html: String): Boolean =
			Regex("\"$flag\"\\s*:\\s*true").containsMatchIn(html)

		fun extractLoginUrls(html: String): List<String> =
			Regex("\"sso[A-Za-z0-9_]*LoginUrl\"\\s*:\\s*\"([^\"]+)\"")
				.findAll(html)
				.map { it.groupValues[1] }
				.toList()
	}
}
