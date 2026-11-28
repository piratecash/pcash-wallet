package cash.p.terminal.detekt

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.RuleSet
import io.gitlab.arturbosch.detekt.api.RuleSetProvider

class CustomRuleSetProvider : RuleSetProvider {
    override val ruleSetId = "custom-rules"
    override fun instance(config: Config) = RuleSet(
        id = ruleSetId,
        rules = listOf(
            UnnecessaryFullyQualifiedNameRule(config),
            AppSingletonAccessRule(config),
            JvmApiInCommonMain(config),
            NotNullAssertion(config),
            ViewModelContextParameter(config),
            RunCatchingGetOrNull(config),
            RawSharedPreferencesEditor(config),
            SynchronizedLock(config),
            NestedViewModelScopeLaunch(config),
            DefaultParameterBeforeRequired(config),
            NestedFunctionType(config),
            ViewModelUiStateName(config),
            ForbiddenCall(config),
        )
    )
}
