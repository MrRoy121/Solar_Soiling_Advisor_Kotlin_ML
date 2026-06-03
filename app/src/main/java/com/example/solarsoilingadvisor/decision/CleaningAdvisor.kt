package com.example.solarsoilingadvisor.decision

/**
 * The recommended action for the user.
 */
enum class CleaningAction { CLEAN_NOW, WAIT }

/**
 * Inputs to the cleaning decision. Defaults are tuned for a typical Gulf-region
 * residential/commercial PV setup; the user can override them in Settings.
 *
 * @param ratedPowerW         panel (or array) rated power, in Watts
 * @param peakSunHours        average daily peak-sun-hours (Gulf ~5.5-6.5)
 * @param tariffPerKwh        electricity value per kWh, in local currency (e.g. SAR)
 * @param soilingFraction     estimated fraction of power lost to soiling, 0.0..1.0
 * @param daysUntilNextCheck  planning horizon: days until the panel is next inspected
 * @param cleaningCost        total cost of one cleaning (water + labor), local currency
 */
data class CleaningInputs(
    val ratedPowerW: Double,
    val peakSunHours: Double = 6.0,
    val tariffPerKwh: Double = 0.18,
    val soilingFraction: Double,
    val daysUntilNextCheck: Int = 7,
    val cleaningCost: Double = 10.0,
)

/**
 * The decision plus the economic figures behind it, so the UI (and the paper)
 * can show *why*, not just *what*.
 */
data class CleaningDecision(
    val action: CleaningAction,
    val dailyRevenueLoss: Double,   // currency lost per day at current soiling
    val projectedLoss: Double,      // currency lost over the planning horizon
    val netBenefit: Double,         // projectedLoss - cleaningCost (positive => clean)
    val breakevenDays: Double,      // days for accrued loss to equal cleaning cost
    val rationale: String,
)

/**
 * Pure decision logic. No Android dependencies => fully unit-testable.
 *
 * Core idea: cleaning is worth it when the energy revenue you would recover over
 * the planning horizon exceeds the cost of cleaning.
 *
 *   dailyEnergy(kWh)   = ratedPowerW/1000 * peakSunHours
 *   dailyLoss(kWh)     = dailyEnergy * soilingFraction
 *   dailyRevenueLoss   = dailyLoss * tariffPerKwh
 *   projectedLoss      = dailyRevenueLoss * daysUntilNextCheck
 *   netBenefit         = projectedLoss - cleaningCost
 *   => CLEAN_NOW if netBenefit > 0, else WAIT
 */
object CleaningAdvisor {

    fun decide(input: CleaningInputs): CleaningDecision {
        require(input.ratedPowerW >= 0) { "ratedPowerW must be >= 0" }
        require(input.soilingFraction in 0.0..1.0) { "soilingFraction must be in 0..1" }
        require(input.daysUntilNextCheck >= 0) { "daysUntilNextCheck must be >= 0" }

        val dailyEnergyKwh = (input.ratedPowerW / 1000.0) * input.peakSunHours
        val dailyLossKwh = dailyEnergyKwh * input.soilingFraction
        val dailyRevenueLoss = dailyLossKwh * input.tariffPerKwh
        val projectedLoss = dailyRevenueLoss * input.daysUntilNextCheck
        val netBenefit = projectedLoss - input.cleaningCost
        val breakevenDays =
            if (dailyRevenueLoss > 0.0) input.cleaningCost / dailyRevenueLoss
            else Double.POSITIVE_INFINITY

        val action = if (netBenefit > 0.0) CleaningAction.CLEAN_NOW else CleaningAction.WAIT

        val rationale = when (action) {
            CleaningAction.CLEAN_NOW ->
                "Soiling is costing ~%.2f/day. Over %d days that is %.2f, which exceeds the %.2f cleaning cost. Clean now."
                    .format(dailyRevenueLoss, input.daysUntilNextCheck, projectedLoss, input.cleaningCost)
            CleaningAction.WAIT ->
                if (breakevenDays.isFinite())
                    "Soiling is costing ~%.2f/day. It takes ~%.1f days to justify the %.2f cleaning cost; you are checking in %d. Wait."
                        .format(dailyRevenueLoss, breakevenDays, input.cleaningCost, input.daysUntilNextCheck)
                else
                    "No measurable soiling loss detected. No cleaning needed."
        }

        return CleaningDecision(
            action = action,
            dailyRevenueLoss = dailyRevenueLoss,
            projectedLoss = projectedLoss,
            netBenefit = netBenefit,
            breakevenDays = breakevenDays,
            rationale = rationale,
        )
    }
}

/**
 * Maps a discrete classifier label to an estimated soiling fraction for the
 * decision layer. Binary models only tell us clean/dusty, so "Dusty" uses a
 * configurable assumed loss. Multi-class severity models can map each class to
 * its own midpoint. Tune these once you have DeepSolarEye severity bins.
 */
object SoilingEstimator {
    // Default assumed loss for a binary "dusty" verdict (15%). Make this user-tunable.
    const val DEFAULT_DUSTY_FRACTION = 0.15

    private val severityMap = mapOf(
        "clean" to 0.0,
        "light" to 0.07,
        "moderate" to 0.20,
        "heavy" to 0.40,
        "dusty" to DEFAULT_DUSTY_FRACTION,
    )

    /**
     * @param dustyFraction override for the binary "dusty" case.
     */
    fun fractionFor(label: String, dustyFraction: Double = DEFAULT_DUSTY_FRACTION): Double {
        val key = label.trim().lowercase()
        if (key == "dusty") return dustyFraction
        return severityMap[key] ?: 0.0
    }
}