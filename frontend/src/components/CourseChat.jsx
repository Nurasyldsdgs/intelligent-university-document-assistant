import AiChat from './AiChat'

const SUGGESTIONS = ['Когда экзамен?', 'Какие лабораторные работы нужно сдать?', 'Как выставляется итоговая оценка?']

// Answers come from all course documents the user may see; each source names its document.
export default function CourseChat({ course }) {
  return (
    <AiChat
      endpoint={`/courses/${course.id}/ask`}
      title="Задать вопрос по курсу"
      subtitle={`Поиск по всем доступным вам документам курса «${course.title}»`}
      placeholder="Например: какие темы будут на экзамене?"
      suggestions={SUGGESTIONS}
      showDocuments
    />
  )
}
