interface FeaturePlaceholderPageProps {
  title: string
  description: string
}

function FeaturePlaceholderPage({
  title,
  description,
}: FeaturePlaceholderPageProps) {
  return (
    <div className="feature-page">
      <header className="page-heading">
        <div>
          <p className="eyebrow">FinTrack</p>
          <h1>{title}</h1>
          <p className="page-heading__description">{description}</p>
        </div>
      </header>

      <section className="content-card feature-placeholder">
        <h2>{title} workspace</h2>
        <p>
          The responsive page structure is ready. We will connect this area to
          the FinTrack API in an upcoming step.
        </p>
      </section>
    </div>
  )
}

export default FeaturePlaceholderPage