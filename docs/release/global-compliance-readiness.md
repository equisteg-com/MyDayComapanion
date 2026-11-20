# Global launch compliance readiness — MyDayCompanion
**Review date:** 2026-11-20  
**Status:** NOT legally approved. This is a compliance engineering checklist, not legal advice or a certification.

## Baseline product assumptions (verify against shipped APK and third-party SDK behavior)
- Local-first Android app. The merged APK is intended to have **no INTERNET permission**; encrypted local Room/SQLCipher storage, on-device AI fallback and owner-approved exports/deletions are implemented.
- Users may enable **phone speech recognition**, which is supplied by a separate system/provider application and **may use the internet** even though MyDayCompanion cannot. Disclosure must clearly distinguish that pathway.
- The app requests microphone, notification, calendar, biometrics/lock and alarm capabilities; audit the precise purpose, runtime permission timing and refusal path for each.
- No paid accounts, ad SDKs or app backend are intended **today**. Never use blanket language such as “no data ever leaves the phone” unless exported files and third-party speech have been excluded explicitly.
- Any future sync, crash reporting, analytics, advertising, LLM API or payments requires a renewed data map, DPIA/PIA where appropriate, contracts, disclosures, consent/rights flow and security review.

## Release-blocking cross-market actions

| ID | Owner | Requirement and evidence | Status |
|---|---|---|---|
| LEG-01 | Founder/legal | Identify actual publishing legal person, country of establishment and public business contact; unregistered project name alone is not an identity | OPEN |
| LEG-02 | Privacy lead | Inventory all data categories/purposes, storage, device services, export targets and recipients; capture SDK evidence and actual APK permissions | OPEN |
| LEG-03 | Privacy lead | Create public, accurate, accessible privacy notice, in-app link, change history and support/contact address; align with Google Play Data safety declaration | OPEN |
| LEG-04 | Android lead | Make voice/cloud speech disclosure just-in-time, consentful, and easily reversible; test service provider fallback | OPEN |
| LEG-05 | QA | Verify access, export, correction, deletion, encrypted backup/restore and data retention against real phones and Android backups | OPEN |
| LEG-06 | Privacy/security | Establish written breach-response workflow, contact escalation, incident log and country-specific notice assessment | OPEN |
| LEG-07 | Product/legal | Choose target audience: if children are targeted or likely users, separately review child privacy and age-appropriate design requirements | OPEN |
| LEG-08 | Security | Produce independent release evidence: SBOM/license review, dependency and secret scan, Android permission inspection, pentest/threat model and signed APK provenance | OPEN |
| LEG-09 | Operations | Document user support/privacy requests, accountable owner, response time procedure and how data on a *user-owned* offline device can be managed | OPEN |
| LEG-10 | Founder/legal | Obtain local counsel's review for each market before an external launch; verify current law and store policies on the release date | OPEN |

## Country-specific legal checkpoints (do not treat as automatically applicable)

### India
DPDP Act 2023 and DPDP Rules 2025 are phased: the Gazette notification of **13 November 2025** provides immediate provisions, a one-year phase, and an eighteen-month phase (around **May 2027**) for many substantive rules. Record the actual commencement provision for each duty, consent notice, grievance handling, child data, breach notice and transfers before launch. Do not assume all rules are already in force, or that a local-only application has no legal obligations.
- Official Act commencement: https://www.meity.gov.in/static/uploads/2025/11/c56ceae6c383460ca69577428d36828b.pdf
- Official Rules: https://www.meity.gov.in/static/uploads/2025/11/53450e6e5dc0bfa85ebd78686cadad39.pdf

### United States
Privacy obligations vary by state, covered-entity tests and use case. Assess California CCPA/CPRA **business applicability** under current revenue and processing thresholds; the CPPA has regulations effective **1 January 2026**. Do not claim CCPA compliance or non-applicability without threshold analysis. Check FTC unfair/deceptive-practices standards; evaluate COPPA (amended 2025) if the service is child-directed or has actual knowledge of covered under-13 processing.
- California: https://cppa.ca.gov/regulations/
- FTC COPPA: https://www.ftc.gov/business-guidance/resources/complying-coppa-frequently-asked-questions

### Canada
Review PIPEDA for covered commercial activity and provincial regimes, notably Quebec, Alberta and British Columbia. Provide meaningful notice and consent when required, security safeguards, appropriate collection purposes and access/correction flows. Determine whether data is *collected by the developer* as distinct from remaining solely on-device.
- OPC mobile app guidance: https://www.priv.gc.ca/en/privacy-topics/technology/mobile-and-digital-devices/mobile-apps/gd_app_201210/
- OPC meaningful consent: https://www.priv.gc.ca/en/privacy-topics/privacy-for-businesses/appropriate-handling-of-personal-information/collecting-personal-information-and-consent/consent/gl_omc_201805/

### United Kingdom
Assess UK GDPR / Data Protection Act applicability and controller/processor roles, lawful basis, accessible privacy information, rights, security and applicable international-transfer duties. Evaluate PECR for any future online storage/access technologies. If likely accessed by children, assess the ICO Children's Code, high-privacy defaults and an appropriate DPIA.
- ICO lawful basis: https://ico.org.uk/for-organisations/uk-gdpr-guidance-and-resources/lawful-basis/a-guide-to-lawful-basis/
- ICO privacy information: https://ico.org.uk/for-organisations/uk-gdpr-guidance-and-resources/individual-rights/the-right-to-be-informed/how-should-we-draft-our-privacy-information
- ICO Children's Code: https://ico.org.uk/for-organisations/uk-gdpr-guidance-and-resources/childrens-information/childrens-code-guidance-and-resources/

### Australia
Assess applicability of Privacy Act 1988 / Australian Privacy Principles; most businesses of **AUD 3m or below** turnover can fall outside general coverage but important exceptions apply (e.g. some health providers). For covered APP entities, privacy notices, access/correction, safeguards and overseas disclosure require examination. OAIC notes additional APP 1 privacy-policy obligations commencing **10 December 2026** for covered automated decisions; investigate if relevant to this agent.
- OAIC coverage: https://www.oaic.gov.au/privacy/privacy-legislation/the-privacy-act
- OAIC APP guidelines: https://www.oaic.gov.au/privacy/australian-privacy-principles/australian-privacy-principles-guidelines

## Distribution and store compliance
Google Play requires a privacy policy on the store listing and inside the app, plus accurately completed declarations, even where the app claims no developer collection. The actual speech service and permitted exported files must be accounted for accurately.
- Play policy: https://support.google.com/googleplay/android-developer/answer/9859455
- Data safety: https://support.google.com/googleplay/android-developer/answer/10787469
- Google Play currently charges a **US$25 one-time developer registration fee** and requires a payment method. It is **not** a no-card option: https://support.google.com/googleplay/android-developer/answer/6112435

## Release acceptance
No external-market launch until LEG-01 through LEG-10 have owners, dated evidence, privacy contact, published notice, verified tests, confirmed target audience and legal reviewer sign-off. Re-check policy changes, release notes, license inventory and permissions before each major version.
