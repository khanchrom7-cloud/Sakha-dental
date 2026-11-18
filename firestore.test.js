const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ADMIN_UID = "super_admin_1";
const DOCTOR_UID = "doctor_1";
const RECEPTION_UID = "reception_1";
const PATIENT_A_UID = "patient_a";
const PATIENT_B_UID = "patient_b";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

async function seedStaffRoles() {
  const now = new Date(Date.now() - 10000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("clinic_bootstrap").doc("lock").set({
      ownerUid: ADMIN_UID,
      createdAt: now,
    });
    await db.collection("staff_roles").doc(ADMIN_UID).set({
      uid: ADMIN_UID,
      email: "khanchrom7@gmail.com",
      displayName: "Dr. Ghulam Sediq Sakha",
      role: "SUPER_ADMIN",
      active: true,
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("staff_roles").doc(DOCTOR_UID).set({
      uid: DOCTOR_UID,
      email: "dr.sakha@clinic.local",
      displayName: "Dr. Sakha",
      role: "DOCTOR",
      active: true,
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("staff_roles").doc(RECEPTION_UID).set({
      uid: RECEPTION_UID,
      email: "reception@clinic.local",
      displayName: "Reception Desk",
      role: "RECEPTIONIST",
      active: true,
      createdAt: now,
      updatedAt: now,
    });
  });
}

test("Guest & Patient Portal: Public doctor bio and services (no prices) are readable; internal pricing is strictly blocked", async () => {
  await seedStaffRoles();
  const now = new Date(Date.now() - 5000);

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("public_doctor_profile").doc("main").set({
      fullNameEn: "Dr. Ghulam Sediq Sakha",
      fullNameFa: "داکتر غلام صدیق سخا",
      titleFa: "معالج امراض جوف دهن و دندان",
      education: ["BDS, Baqai Medical University, Karachi, Pakistan, 2020"],
      training: ["C_Endo", "C_Ortho", "C_Implant", "ClearPath clear aligner training"],
      licenses: ["Afghanistan Medical Council professional activity license"],
      experience: ["Founder and responsible director of Sakha Specialised Dental Centre"],
      published: true,
      version: 1,
      updatedAt: now,
    });
    await db.collection("public_services").doc("srv_rct").set({
      nameEn: "Root Canal Treatment",
      nameFa: "عصب‌کشی دندان",
      namePs: "د غاښ عصب کشي",
      category: "Endodontics",
      descriptionEn: "Modern painless root canal therapy",
      descriptionFa: "تداوی ریشه دندان با تکنالوژی پیشرفته",
      descriptionPs: "په عصري وسایلو د غاښ عصب درملنه",
      generalInfoEn: "Multi-step canal cleaning and obturation",
      generalInfoFa: "پاک‌سازی و پرکاری تخصصی کانال ریشه",
      generalInfoPs: "د ریښې کانال پاکول او ډکول",
      imageKey: "endodontics",
      published: true,
      sortOrder: 1,
      updatedAt: now,
    });
    await db.collection("internal_service_pricing").doc("srv_rct").set({
      serviceId: "srv_rct",
      serviceName: "Root Canal Treatment",
      category: "Endodontics",
      unitPriceAfn: 2000,
      bridgeUnitPriceAfn: 10000,
      active: true,
      updatedAt: now,
    });
  });

  // Unauthenticated Guest reads public bio & public services cleanly
  const guestDb = testEnv.unauthenticatedContext().firestore();
  const docSnap = await assertSucceeds(guestDb.collection("public_doctor_profile").doc("main").get());
  assert.equal(docSnap.data().fullNameEn, "Dr. Ghulam Sediq Sakha");
  const srvSnap = await assertSucceeds(guestDb.collection("public_services").get());
  assert.equal(srvSnap.size, 1);
  assert.equal("unitPriceAfn" in srvSnap.docs[0].data(), false);

  // Guest CANNOT read internal_service_pricing
  await assertFails(guestDb.collection("internal_service_pricing").get());
  await assertFails(guestDb.collection("internal_service_pricing").doc("srv_rct").get());

  // Patient CANNOT read internal_service_pricing either
  const patientDb = testEnv.authenticatedContext(PATIENT_A_UID).firestore();
  await assertSucceeds(patientDb.collection("public_services").get());
  await assertFails(patientDb.collection("internal_service_pricing").get());

  // Super Admin CAN read internal pricing, AND cannot accidentally inject a price field into public_services
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
  await assertSucceeds(adminDb.collection("internal_service_pricing").get());
  await assertFails(
    adminDb.collection("public_services").doc("srv_implant").set({
      nameEn: "Dental Implant",
      nameFa: "ایمپلنت",
      category: "Implantology",
      unitPriceAfn: 25000, // FORBIDDEN in public_services!
      published: true,
      sortOrder: 2,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("RBAC & Bootstrap Lock: Prevents privilege escalation and blocks public Super Admin registration", async () => {
  await seedStaffRoles();
  const now = new Date(Date.now() - 1000);
  const attackerDb = testEnv.authenticatedContext(PATIENT_A_UID).firestore();

  // Attacker cannot register as SUPER_ADMIN once bootstrap lock exists
  await assertFails(
    attackerDb.collection("staff_roles").doc(PATIENT_A_UID).set({
      uid: PATIENT_A_UID,
      email: "attacker@example.com",
      displayName: "Attacker",
      role: "SUPER_ADMIN",
      active: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Patient cannot set linkedPatientId on their own user profile
  await assertFails(
    attackerDb.collection("users").doc(PATIENT_A_UID).set({
      userId: PATIENT_A_UID,
      displayName: "Patient A",
      preferredLanguage: "DARI",
      calendarMode: "SOLAR_HIJRI",
      linkedPatientId: "pat_1",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Receptionist cannot access payroll or audit logs
  const receptionDb = testEnv.authenticatedContext(RECEPTION_UID).firestore();
  await assertFails(receptionDb.collection("payroll").get());
  await assertFails(receptionDb.collection("audit_logs").get());
});

test("Patient Isolation & Internal Clinical Notes: Patients see only own records and never internal_notes", async () => {
  await seedStaffRoles();
  const now = new Date(Date.now() - 5000);

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("patients").doc("pat_a").set({
      patientCode: "SKH-1001",
      fullName: "Ahmad",
      phone: "0780000001",
      age: 30,
      linkedUserUid: PATIENT_A_UID,
      archived: false,
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("patients").doc("pat_a").collection("internal_notes").doc("note_1").set({
      note: "Private clinician differential note",
      createdAt: now,
    });
    await db.collection("patients").doc("pat_b").set({
      patientCode: "SKH-1002",
      fullName: "Mahmood",
      phone: "0780000002",
      age: 40,
      linkedUserUid: PATIENT_B_UID,
      archived: false,
      createdAt: now,
      updatedAt: now,
    });
  });

  const patientADb = testEnv.authenticatedContext(PATIENT_A_UID).firestore();
  await assertSucceeds(
    patientADb.collection("patients").where("linkedUserUid", "==", PATIENT_A_UID).get()
  );
  await assertFails(patientADb.collection("patients").doc("pat_b").get());
  await assertFails(patientADb.collection("patients").get()); // unfiltered
  await assertFails(
    patientADb.collection("patients").doc("pat_a").collection("internal_notes").get()
  );

  const doctorDb = testEnv.authenticatedContext(DOCTOR_UID).firestore();
  await assertSucceeds(
    doctorDb.collection("patients").doc("pat_a").collection("internal_notes").get()
  );
});

test("MTU Receipt Upload & Manual Verification: Patient cannot self-approve; terminal lock prevents duplicate approval", async () => {
  await seedStaffRoles();
  const now = new Date(Date.now() - 5000);
  const patientDb = testEnv.authenticatedContext(PATIENT_A_UID).firestore();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();

  // Patient submits single screenshot receipt (PENDING_VERIFICATION, approvedAmountAfn = 0)
  await assertSucceeds(
    patientDb.collection("mtu_receipts").doc("rcpt_1").set({
      userId: PATIENT_A_UID,
      patientId: "pat_a",
      patientName: "Ahmad",
      appointmentId: "appt_1",
      invoiceId: "inv_1",
      receiptImageBase64: "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJ",
      status: "PENDING_VERIFICATION",
      approvedAmountAfn: 0,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Patient CANNOT submit pre-approved MTU receipt or approve their own receipt
  await assertFails(
    patientDb.collection("mtu_receipts").doc("rcpt_fake").set({
      userId: PATIENT_A_UID,
      patientName: "Ahmad",
      receiptImageBase64: "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJ",
      status: "APPROVED",
      approvedAmountAfn: 150,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(
    patientDb.collection("mtu_receipts").doc("rcpt_1").update({
      status: "APPROVED",
      approvedAmountAfn: 150,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Admin approves receipt ONCE
  await assertSucceeds(
    adminDb.collection("mtu_receipts").doc("rcpt_1").update({
      status: "APPROVED",
      approvedAmountAfn: 150,
      reviewedBy: ADMIN_UID,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Terminal lock prevents duplicate re-approval or re-mutation!
  await assertFails(
    adminDb.collection("mtu_receipts").doc("rcpt_1").update({
      status: "APPROVED",
      approvedAmountAfn: 300,
      updatedAt: new Date(Date.now() - 500),
    })
  );
});
