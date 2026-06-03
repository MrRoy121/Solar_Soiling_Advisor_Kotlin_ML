package com.example.solarsoilingadvisor.decision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CleaningAdvisorTest {

    @Test
    fun `heavy soiling on large array recommends cleaning`() {
        val decision = CleaningAdvisor.decide(
            CleaningInputs(
                ratedPowerW = 5000.0,       // 5 kW array
                peakSunHours = 6.0,
                tariffPerKwh = 0.18,
                soilingFraction = 0.30,     // 30% loss
                daysUntilNextCheck = 14,
                cleaningCost = 10.0,
            )
        )
        assertEquals(CleaningAction.CLEAN_NOW, decision.action)
        assertTrue("net benefit should be positive", decision.netBenefit > 0)
    }

    @Test
    fun `tiny loss on small panel recommends waiting`() {
        val decision = CleaningAdvisor.decide(
            CleaningInputs(
                ratedPowerW = 300.0,        // single small panel
                peakSunHours = 6.0,
                tariffPerKwh = 0.18,
                soilingFraction = 0.03,     // 3% loss
                daysUntilNextCheck = 3,
                cleaningCost = 10.0,
            )
        )
        assertEquals(CleaningAction.WAIT, decision.action)
        assertTrue("net benefit should be negative", decision.netBenefit < 0)
    }

    @Test
    fun `zero soiling yields infinite breakeven and wait`() {
        val decision = CleaningAdvisor.decide(
            CleaningInputs(
                ratedPowerW = 5000.0,
                soilingFraction = 0.0,
                cleaningCost = 10.0,
            )
        )
        assertEquals(CleaningAction.WAIT, decision.action)
        assertTrue(decision.breakevenDays.isInfinite())
        assertEquals(0.0, decision.dailyRevenueLoss, 1e-9)
    }

    @Test
    fun `daily revenue loss math is correct`() {
        // 1 kW * 6 PSH = 6 kWh/day; 20% loss = 1.2 kWh/day; * 0.18 = 0.216/day
        val decision = CleaningAdvisor.decide(
            CleaningInputs(
                ratedPowerW = 1000.0,
                peakSunHours = 6.0,
                tariffPerKwh = 0.18,
                soilingFraction = 0.20,
                daysUntilNextCheck = 10,
                cleaningCost = 100.0,
            )
        )
        assertEquals(0.216, decision.dailyRevenueLoss, 1e-6)
        assertEquals(2.16, decision.projectedLoss, 1e-6)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `soiling fraction out of range throws`() {
        CleaningAdvisor.decide(
            CleaningInputs(ratedPowerW = 1000.0, soilingFraction = 1.5, cleaningCost = 10.0)
        )
    }

    @Test
    fun `estimator maps labels to fractions`() {
        assertEquals(0.0, SoilingEstimator.fractionFor("Clean"), 1e-9)
        assertEquals(0.15, SoilingEstimator.fractionFor("Dusty"), 1e-9)
        assertEquals(0.25, SoilingEstimator.fractionFor("Dusty", dustyFraction = 0.25), 1e-9)
        assertEquals(0.40, SoilingEstimator.fractionFor("heavy"), 1e-9)
    }
}