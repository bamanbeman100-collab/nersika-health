package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.ai.AiAccessDeniedException
import com.example.data.ai.AiClinicalRequest
import com.example.data.ai.AiFailureReason
import com.example.data.ai.AiTaskType
import com.example.data.ai.GeminiAiRepository
import com.example.data.ai.GeminiModelGateway
import com.example.data.local.DemoDataSeeder
import com.example.data.local.NersikaDatabase
import com.example.domain.model.ClinicalSafetyEngine
import com.example.i18n.AppLanguage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var db: NersikaDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, NersikaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `app name matches Nersika Health`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("Nersika Health", context.getString(R.string.app_name))
    }

    @Test
    fun `demo seeder populates roles, patients, and AI policy respects consent`() = runTest {
        val dao = db.dao()
        DemoDataSeeder.seedIfEmpty(dao)

        val users = dao.observeAllUsers().first()
        assertEquals(5, users.size)

        val physician = users.first { it.role == "PHYSICIAN" }
        val sara = dao.getPatientById("pat_001")
        assertNotNull(sara)
        assertTrue(sara!!.consentClinicianAccess)

        val mina = dao.getPatientById("pat_003")
        assertNotNull(mina)
        assertFalse(mina!!.consentClinicianAccess)
        assertFalse(mina.consentAiSummarization)

        val meds = dao.observeMedications("pat_001").first()
        val allergies = dao.observeAllergies("pat_001").first()
        val labs = dao.observeLabResults("pat_001").first()
        val conditions = dao.observeConditions("pat_001").first()
        val obs = dao.observeObservations("pat_001").first()
        val report = ClinicalSafetyEngine.evaluatePatient(sara, meds, allergies, conditions, labs, emptyList())

        val gateway = object : GeminiModelGateway {
            override suspend fun generateClinicalJson(
                systemInstruction: String,
                prompt: String,
                image: android.graphics.Bitmap?
            ): String {
                return """
                    {
                      "title": "خلاصه پرونده سارا",
                      "verifiedClinicalFacts": ["فشار خون و کنترل متابولیک [SRC:PATIENT_${sara.id}]"],
                      "patientReportedAndUnverifiedInfo": [],
                      "aiInterpretation": "خلاصه بر اساس داده‌های پرونده برای بررسی پزشک آماده شده است.",
                      "clinicianRecommendations": [],
                      "missingOrUncertainData": [],
                      "citedSourceIds": ["SRC:PATIENT_${sara.id}"]
                    }
                """.trimIndent()
            }
        }
        val aiRepo = GeminiAiRepository(gateway)
        val saraAi = aiRepo.generateClinicalAssistance(
            AiClinicalRequest(
                taskType = AiTaskType.SUMMARIZE_RECORD,
                language = AppLanguage.FA,
                actor = physician,
                patient = sara,
                conditions = conditions,
                medications = meds,
                allergies = allergies,
                labs = labs,
                observations = obs,
                alerts = report.alerts
            )
        )
        assertTrue(saraAi.requiresClinicianReview)
        assertTrue(saraAi.title.contains("خلاصه پرونده سارا"))

        val denied = runCatching {
            aiRepo.generateClinicalAssistance(
                AiClinicalRequest(
                    taskType = AiTaskType.SUMMARIZE_RECORD,
                    language = AppLanguage.EN,
                    actor = physician,
                    patient = mina,
                    conditions = emptyList()
                )
            )
        }.exceptionOrNull()
        assertTrue(denied is AiAccessDeniedException)
        assertEquals(AiFailureReason.RECORD_ACCESS_DENIED, (denied as AiAccessDeniedException).reason)
    }
}
