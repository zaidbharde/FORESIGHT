export default function AnalyticsPage() {
  const data = [
    { label: "Model Recall", value: "0.7534", fill: 75.34 },
    { label: "ROC-AUC", value: "0.8666", fill: 86.66 },
    { label: "LOW", value: "Risk-aware", fill: 28 },
    { label: "MEDIUM", value: "Warnings", fill: 46 },
    { label: "HIGH", value: "Verification", fill: 68 },
    { label: "CRITICAL", value: "Strong Scam Warning", fill: 90 },
  ];

  return (
    <div className="panel pad">
      <h2 className="page-title">Analytics</h2>
      <p className="muted">
        Production model overview and risk distribution for the explainability-optimized XGBoost system.
      </p>

      <div className="charts section">
        <div className="widget">
          <h3>Model Performance</h3>
          <div className="list">
            <div className="list-item"><strong>Recall:</strong> 0.7534</div>
            <div className="list-item"><strong>ROC-AUC:</strong> 0.8666</div>
            <div className="list-item"><strong>Selected Model:</strong> explainability_xgboost.pkl</div>
          </div>
        </div>

        <div className="widget">
          <h3>Risk Category Distribution</h3>
          <div className="bar-wrap">
            {data.map((item) => (
              <div className="bar-row" key={item.label}>
                <div style={{ display: "flex", justifyContent: "space-between", gap: 12 }}>
                  <span>{item.label}</span>
                  <span className="muted">{item.value}</span>
                </div>
                <div className="bar-track">
                  <div className="bar-fill" style={{ width: `${item.fill}%` }} />
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>

      <div className="section widget">
        <h3>Explainability Model Information</h3>
        <div className="grid-2">
          <div className="list-item">XGBoost for strong tabular fraud detection performance.</div>
          <div className="list-item">SHAP for transparent feature-level risk explanations.</div>
          <div className="list-item">Behavioral signals prioritized over technical identifiers.</div>
          <div className="list-item">OTP scam rules take precedence over model output.</div>
        </div>
      </div>
    </div>
  );
}
