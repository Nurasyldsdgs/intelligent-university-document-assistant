import AiChat from './AiChat'

const SUGGESTIONS = ['О чём этот документ?', 'Какие есть дедлайны?', 'Какие требования к лабораторным работам?']

export default function DocumentChat({ doc }) {
  return (
    <AiChat
      endpoint={`/documents/${doc.id}/ask`}
      title="Чат с ИИ по документу"
      subtitle={doc.originalFileName}
      placeholder="Например: когда сдавать лабораторную работу 2?"
      suggestions={SUGGESTIONS}
      disabledReason={doc.hasExtractedText ? undefined : 'В документе нет распознанного текста — ИИ не сможет ответить по нему.'}
    />
  )
}
