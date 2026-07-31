package plantuml.boundary

import contracts.i18n.TranslationRequest
import contracts.i18n.TranslationResult
import contracts.i18n.TranslationService

class PlantumlTranslationServiceAdapter(
    private val resolver: TranslationResolver,
) : TranslationService {

    override fun translate(request: TranslationRequest): TranslationResult {
        val resolved = resolver.resolve(request.sourceText, request.targetLanguage)
        return if (resolved.translated == request.sourceText && resolved.strategy == TranslationStrategy.PRESERVE) {
            TranslationResult.Failure("text preserved (no translation available for '${request.targetLanguage}')")
        } else {
            TranslationResult.Success(resolved.translated)
        }
    }
}
