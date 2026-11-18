# Sakha Specialised Dental Centre — Ecosystem Architecture & Delivery Report (v6.0)

**Official English Name:** Sakha Specialised Dental Centre  
**Official Dari Name:** مرکز تخصصی دندان و ایمپلنت سخا  
**Official Motto:** سخا، نماد تخصص، زیبایی و اعتماد  
**Address:** شهر شبرغان، عقب تانک تیل دولتی، کوچه جن محله، ولایت جوزجان، افغانستان  
**Contact:** `0782221100` | `0796331611` | `sakha.dentalcenter26@gmail.com`

---

## 1. System Architecture & Technology Stack
- **Android Mobile App (Kotlin + Jetpack Compose + Material 3):**
  - Edge-to-edge UI supporting compact phones (`NavigationBar`) and tablets/desktops (`NavigationRail`), full RTL (`Dari`, `Pashto`) and LTR (`English`) directionality, and bundled typography (`Vazirmatn`, `Plus Jakarta Sans`, `JetBrains Mono`).
  - **Local Persistence & Offline Queue:** `Room` (`SakhaLocalDatabase`) + `DataStore Preferences` (`ClinicPreferencesStore`) + Firestore built-in persistent cache.
- **Centralized Backend (Cloud Firestore + Firebase Auth + App Check + AI Logic):**
  - Dedicated Firestore database instance (`ai-studio-android-sakhaden-a2b1d25e-9688-458b-b4bc-af97981e6e40`) with strict Zero-Trust `firestore.rules`.
  - **Authentication:** Google Sign-In via Jetpack `CredentialManager` (`GetSignInWithGoogleOption` / `GetGoogleIdOption`) — anonymous login disabled and never used.
  - **Firebase AI Logic:** `Firebase.ai.generativeModel("gemini-3.8-flash")` protected by App Check (`DebugAppCheckProviderFactory` in debug / `RecaptchaAppCheckProviderFactory` in release) providing radiograph & case clinical decision support with mandatory clinician review disclaimer.
- **Future iOS & Connected Website Readiness:**
  - Strict separation between UI Composables, MVVM `SakhaAppViewModel`, pure domain business logic (`ClinicBusinessRules`, `ClinicCalendar`, `CurrencyManager`), and public CMS collections (`/public_clinic_profile`, `/public_doctor_profile`, `/public_services`, `/public_media`) so an iOS SwiftUI client or web frontend reads the identical backend schema.

---

## 2. Database Schema & Backend Separation
- **Public CMS Collections (Read-only for Guests/Patients, ZERO Prices):**
  - `/public_clinic_profile/main` — Clinic branding, motto, contact info, hours, announcements, website publish toggle.
  - `/public_doctor_profile/main` — Centrally managed biography for **Dr. Ghulam Sediq Sakha** (`داکتر غلام صدیق سخا`).
  - `/public_services/{id}` — Public services catalogue; **Firestore rules explicitly reject writes containing price/rate/cost keys**.
  - `/public_media/{id}` — Patient-consented gallery & educational items.
- **Staff-Only Pricing & Configuration:**
  - `/internal_service_pricing/{id}` — Unit rates & bridge rates accessible strictly to authenticated staff (`isStaff()`).
  - `/clinic_settings/main` — Configurable bridge default (`10,000 AFN`), deposit (`150 AFN`), late threshold (`30 min`), and exchange rates (`AFN`, `USD`, `EUR`, `PKR`, `IRR`).
- **RBAC, Clinical, & Accounting Collections:**
  - `/clinic_bootstrap/lock` — Protected one-time initial Super Admin lock (`create` blocked if lock already exists).
  - `/staff_roles/{uid}`, `/users/{uid}`, `/patient_link_requests/{id}`, `/partner_links/{id}`.
  - `/patients/{id}` (+ `/internal_notes/{noteId}` staff-only subcollection), `/treatments/{id}`, `/orthodontic_cases/{id}`, `/implant_records/{id}`, `/lab_orders/{id}`, `/prescriptions/{id}`, `/medicines/{id}`.
  - `/appointments/{id}`, `/mtu_receipts/{id}`, `/payments/{id}`, `/invoices/{id}`, `/expenses/{id}`, `/suppliers/{id}`, `/payroll/{id}`, `/notifications/{id}`, `/feedback_complaints/{id}`, `/audit_logs/{id}`, `/backups/{id}`.

---

## 3. Core Implemented Workflows & Safeguards
1. **Multilingual & Dual Calendar:** Complete Dari, Pashto, and English UI with runtime switching, Gregorian + Afghan Solar Hijri date conversion, and 12-hour AM/PM slots.
2. **Role-Based Access & Preview Mode:** 7 roles (`SUPER_ADMIN`, `SENIOR_ADMIN`, `ADMIN`, `DOCTOR`, `RECEPTIONIST`, `PATIENT`, `GUEST`). Owner can inspect any role via `Role Preview Mode` without mutating permanent permissions.
3. **Interactive 32-Tooth FDI Chart & Treatment Pricing:** 6 distinct treatment colors (Blue=Root Canal, Purple=Crown, Red=Extraction, Orange=Scaling, Gold=Implant, Green=Composite), automatic `Selected Teeth × Unit Price`, Dental Bridge builder (`abutment + pontics × bridge unit rate`), and step-by-step completion percentages.
4. **Simple MTU Payment Upload & Atomic Verification:**
   - Patient side has a streamlined 1-action receipt image upload (`Pending Verification`).
   - Admin side includes pinch-to-zoom full-size receipt inspection, transactional `approveMtuReceipt` (prevents duplicate approvals and atomically creates `CONFIRMED` payment + updates invoice balance + notifies patient), and `rejectMtuReceipt` with mandatory reason. Pending/rejected receipts never count toward clinic revenue.
5. **Prescriptions & Official PDF:** Generates printable/shareable A4 clinic PDF embedding `sakha_logo.png` and `doctor_signature.png`.

---

## 4. Setup & Operations Guide
- **First-Time Super Admin Bootstrap:** Sign in with the owner Google account (`khanchrom7@gmail.com`) -> tap **Initialize** on the one-time protected setup banner to seed `/clinic_bootstrap/lock`, initial Clinic Profile, Doctor Profile, Public Services, and Internal Pricing.
- **Website & Doctor Profile Publishing:** Super Admin -> **Control Panel** -> **Clinic/Web CMS** or **Doctor CMS** -> update content and toggle `Published`. Guest Portal, Patient Portal, and Website Preview update in real time.
- **Backups & Offline Sync:** Super Admin -> **Control Panel** -> **Backup & Audit** -> **Create Backup** exports full JSON snapshots to `/backups`; **Sync Now** flushes pending Room offline actions.
- **External Integrations Notice:** Live Google Sign-In, cloud Firestore sync, and Firebase AI Logic calls depend on network connectivity, user authentication on a device with Google Play Services, and active Firebase billing/App Check configuration in production.
