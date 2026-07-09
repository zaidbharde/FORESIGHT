import { useMemo, useState } from "react";
import LoadingSpinner from "../components/LoadingSpinner";
import RiskBadge from "../components/RiskBadge";
import DashboardWidget from "../components/DashboardWidget";

const defaultForm = {
  TransactionAmt: "",
  hour_of_day: "",
  DeviceType: "mobile",
  ProductCD: "W",
  device_change_flag: "0",
  transactions_last_hour: "",
  is_large_amount: "0",
};

const baseModelFields = {
  card1: 2803,
  card2: 100,
  card3: 150,
  card4: "visa",
  card5: 226,
  card6: "debit",
  addr1: 494,
  addr2: 87,
  is_late_night: 0,
  is_round_amount: 0,
  amount_to_average_ratio: 1,
  transactions_last_day: 63,
  days_since_last_transaction: 0.0088310185185185,
};

const productOptions = [
  { value: "W", label: "Online shopping" },
  { value: "C", label: "Card / checkout payment" },
  { value: "R", label: "Recharge or bill payment" },
  { value: "H", label: "Household / service payment" },
  { value: "S", label: "Other service payment" },
];

const deviceOptions = [
  { value: "mobile", label: "Mobile phone" },
  { value: "desktop", label: "Computer / laptop" },
  { value: "Unknown", label: "Not sure" },
];

const yesNoOptions = [
  { value: "0", label: "No" },
  { value: "1", label: "Yes" },
];

const toNumber = (value, fallback = 0) => {
  if (value === "" || value == null) return fallback;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : fallback;
};

export default function FraudCheckPage({ apiBaseUrl, onPrediction, onApiError }) {
  const [form, setForm] = useState(defaultForm);
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState(null);
  const [error, setError] = useState("");

  const riskClass = useMemo(
    () => (result?.risk_category ? `risk-${result.risk_category.toLowerCase()}` : ""),
    [result],
  );

  const updateField = (field, value) => {
    setForm((current) => ({ ...current, [field]: value }));
  };

  const buildPayload = () => {
    const amount = toNumber(form.TransactionAmt);
    const hour = Math.min(23, Math.max(0, toNumber(form.hour_of_day)));

    return {
      ...baseModelFields,
      TransactionAmt: amount,
      hour_of_day: hour,
      DeviceType: form.DeviceType,
      ProductCD: form.ProductCD,
      device_change_flag: toNumber(form.device_change_flag),
      transactions_last_hour: toNumber(form.transactions_last_hour),
      is_large_amount: toNumber(form.is_large_amount),
      is_late_night: hour >= 22 || hour <= 5 ? 1 : 0,
      is_round_amount: amount > 0 && amount % 100 === 0 ? 1 : 0,
      amount_to_average_ratio: amount >= 5000 ? 2.5 : 1,
    };
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    setLoading(true);
    setError("");

    try {
      const response = await fetch(`${apiBaseUrl}/predict`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(buildPayload()),
      });

      if (!response.ok) {
        const data = await response.json().catch(() => ({}));
        throw new Error(data.detail || `Prediction request failed (${response.status})`);
      }

      const data = await response.json();
      setResult(data);
      onPrediction({ label: `${data.risk_category} (${data.risk_score})`, score: data.risk_score });
    } catch (caughtError) {
      const message = caughtError instanceof Error ? caughtError.message : "Prediction failed";
      setError(message);
      onApiError(message);
    } finally {
      setLoading(false);
    }
  };

  const handleReset = () => {
    setForm(defaultForm);
    setResult(null);
    setError("");
  };

  return (
    <div className="panel pad">
      <h2 className="page-title">Fraud Check</h2>
      <p className="muted">Enter the basic payment details a normal user can understand.</p>

      <form className="form" onSubmit={handleSubmit}>
        <div className="form-grid">
          <InputField
            label="Amount"
            type="number"
            value={form.TransactionAmt}
            min="1"
            placeholder="500"
            onChange={(value) => updateField("TransactionAmt", value)}
            required
          />
          <InputField
            label="Payment time"
            type="number"
            value={form.hour_of_day}
            min="0"
            max="23"
            placeholder="0 to 23"
            onChange={(value) => updateField("hour_of_day", value)}
            required
          />
          <SelectField
            label="Device used"
            value={form.DeviceType}
            onChange={(value) => updateField("DeviceType", value)}
            options={deviceOptions}
          />
          <SelectField
            label="Payment type"
            value={form.ProductCD}
            onChange={(value) => updateField("ProductCD", value)}
            options={productOptions}
          />
          <SelectField
            label="Using a new device?"
            value={form.device_change_flag}
            onChange={(value) => updateField("device_change_flag", value)}
            options={yesNoOptions}
          />
          <InputField
            label="Payments in last hour"
            type="number"
            value={form.transactions_last_hour}
            min="0"
            placeholder="0"
            onChange={(value) => updateField("transactions_last_hour", value)}
          />
          <SelectField
            label="Is this amount unusually high?"
            value={form.is_large_amount}
            onChange={(value) => updateField("is_large_amount", value)}
            options={yesNoOptions}
          />
        </div>

        <div className="actions">
          <button className="btn primary" type="submit" disabled={loading}>
            {loading ? <LoadingSpinner label="Analyzing..." /> : "Run Fraud Check"}
          </button>
          <button className="btn secondary" type="button" onClick={handleReset}>
            Reset
          </button>
        </div>
      </form>

      {error ? (
        <div className="alert" style={{ marginTop: 16 }}>
          Error: {error}
        </div>
      ) : null}

      {result ? (
        <div className="section">
          {result.blocked_by_rule_engine ? (
            <div className="alert">Known Scam Pattern Detected - {result.reason}</div>
          ) : null}
          <div className={`risk-card ${riskClass}`}>
            <div className="badge-row">
              <RiskBadge category={result.risk_category} />
              <span className="badge primary">Risk Score: {result.risk_score}</span>
            </div>
            <div className="grid-2">
              <DashboardWidget
                title="Fraud Probability"
                value={
                  result.fraud_probability != null
                    ? `${(result.fraud_probability * 100).toFixed(2)}%`
                    : "100.00%"
                }
              />
              <DashboardWidget title="Recommended Action" value={result.recommended_action} />
            </div>
            <div className="section">
              <h3 style={{ marginTop: 0 }}>Top Reasons</h3>
              <div className="list">
                {(result.top_reasons || []).map((reason) => (
                  <div className="list-item" key={reason}>
                    {reason}
                  </div>
                ))}
              </div>
            </div>
          </div>
        </div>
      ) : null}
    </div>
  );
}

function InputField({ label, type, value, onChange, placeholder, min, max, required }) {
  return (
    <label className="field">
      <span>{label}</span>
      <input
        className="input"
        type={type}
        value={value}
        min={min}
        max={max}
        placeholder={placeholder}
        required={required}
        onChange={(event) => onChange(event.target.value)}
      />
    </label>
  );
}

function SelectField({ label, value, onChange, options }) {
  return (
    <label className="field">
      <span>{label}</span>
      <select className="select" value={value} onChange={(event) => onChange(event.target.value)}>
        {options.map((option) => (
          <option value={option.value} key={option.value}>
            {option.label}
          </option>
        ))}
      </select>
    </label>
  );
}
