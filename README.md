<div align="center">

<img src="https://raw.githubusercontent.com/zaidbharde/FORESIGHT/main/assets/app_logo.png" alt="FORESIGHT Logo" width="120"/>

# FORESIGHT

### 🛡️ AI-Powered Adaptive Fraud Prevention for UPI Payments

[![Python](https://img.shields.io/badge/Python-3.11+-3776AB?style=for-the-badge&logo=python&logoColor=white)](https://python.org)
[![FastAPI](https://img.shields.io/badge/FastAPI-0.110+-009688?style=for-the-badge&logo=fastapi&logoColor=white)](https://fastapi.tiangolo.com)
[![React](https://img.shields.io/badge/React-18+-61DAFB?style=for-the-badge&logo=react&logoColor=black)](https://reactjs.org)
[![XGBoost](https://img.shields.io/badge/XGBoost-ML_Model-FF6600?style=for-the-badge&logo=data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAyNCAyNCI+PC9zdmc+)](https://xgboost.ai)
[![License](https://img.shields.io/badge/License-MIT-green?style=for-the-badge)](LICENSE)

**A smarter alternative to the RBI's proposed 1-hour cooling-off period for high-value UPI transactions.**

[📱 Live Demo](#demo) · [📖 Docs](#api-reference) · [🚀 Quick Start](#quick-start) · [🎯 Pitch Deck](#pitch)

</div>

---

## 🎯 The Problem

The RBI proposed a **mandatory 1-hour delay** for UPI transactions above ₹10,000 to reduce fraud. While well-intentioned, this creates a critical gap:

| Scenario | RBI Approach | FORESIGHT |
|----------|-------------|-----------|
| 🏥 Hospital emergency payment | ❌ 1 hour wait | ✅ Instant (trusted) |
| 💼 Urgent business transfer | ❌ 1 hour wait | ✅ Verified in seconds |
| ⚠️ First-time suspicious transfer | ⏳ Still delayed | 🛡️ Actively blocked |
| ✅ Regular trusted contact | ❌ Also delayed | ✅ Zero friction |

> **FORESIGHT replaces a blunt time-delay with intelligent, risk-proportional verification — like email 2FA, but smarter.**

---

## ✨ How It Works

```
User initiates payment
        │
        ▼
┌───────────────────┐
│  OTP Fraud Shield │  ← Catches social engineering BEFORE scoring
│  (Pre-screening)  │
└────────┬──────────┘
         │
         ▼
┌───────────────────┐
│   Risk Engine     │  ← ML model scores 0–100 in real time
│  XGBoost + SHAP   │
└────────┬──────────┘
         │
    ┌────┴─────────────────────────┐
    │                              │
    ▼                              ▼
Score 0–29                    Score 30–59
  LOW RISK                   MEDIUM RISK
  PIN only                   Review screen
  Instant ✅                 Hold-to-confirm ⚠️
                                   │
                              Score 60–79
                              HIGH RISK
                              OTP + Warning 🔴
                                   │
                              Score 80+
                              CRITICAL
                              Freeze + 1930 🚨
```

---

## 🧠 ML Architecture

```
IEEE-CIS Dataset (590K transactions)
           │
           ▼
   Feature Engineering
   ┌─────────────────────────────────────┐
   │ • Hour of day from TransactionDT    │
   │ • Is late night flag (12am–6am)     │
   │ • Amount-to-user-average ratio      │
   │ • Is round amount flag              │
   │ • First-time beneficiary flag       │
   │ • Transaction frequency (1hr / 24hr)│
   │ • Device change flag                │
   │ • Location anomaly                  │
   └─────────────────────────────────────┘
           │
           ▼
   ┌───────────────┐     ┌───────────────┐
   │ Random Forest │  vs │    XGBoost    │  ← Winner deployed
   │  (Baseline)   │     │  (Production) │
   └───────────────┘     └───────────────┘
           │
           ▼
   SHAP Explainability
   "New beneficiary contributed 34% to risk"
   "Late night transaction contributed 28%"
   "Amount 3x above average contributed 22%"
```

---

## 🛡️ OTP Fraud Shield

India's #1 UPI scam vector — social engineering via phone calls — gets its own dedicated layer **before** the ML model runs:

- 📞 **Active call detection** — warns if a call is ongoing during payment
- 🚫 **Receive-money OTP blocker** — "You never need OTP to receive money"
- 🔄 **SIM change lock** — restricts high-value transfers for 7 days after SIM change
- ⏱️ **90-second OTP expiry** — eliminates the manipulation window
- ❓ **"Who Asked You?" screen** — 4-option confirmation before any OTP entry

---

## 🏗️ Project Structure

```
FORESIGHT/
│
├── 📱 frontend/                    # React 18 + Tailwind + Framer Motion
│   ├── src/
│   │   ├── screens/               # 11 app screens
│   │   ├── components/            # Shared UI components
│   │   ├── utils/                 # Risk engine (JS rule-based)
│   │   └── constants/             # Dummy data, contacts, scenarios
│   └── package.json
│
├── ⚡ backend/                     # FastAPI + ML serving
│   ├── app/
│   │   ├── api/
│   │   │   └── predict.py         # /predict endpoint
│   │   ├── core/
│   │   │   └── config.py          # App configuration
│   │   ├── schemas/
│   │   │   ├── request.py         # Pydantic input models
│   │   │   └── response.py        # Pydantic output models
│   │   └── services/
│   │       └── prediction_service.py  # ML inference + SHAP
│   └── main.py
│
├── 🧠 ml-training/                 # Offline training pipeline
│   ├── data/                      # IEEE-CIS dataset (local only)
│   ├── train_model.py             # RF + XGBoost training
│   ├── feature_engineering.py     # Feature pipeline
│   └── evaluate.py                # Metrics + confusion matrix
│
└── 📊 model/
    └── foresight_model.pkl        # Trained model artifact
```

---

## 🚀 Quick Start

### Prerequisites

```bash
Python 3.11+
Node.js 18+
```

### Backend Setup

```bash
# Clone the repo
git clone https://github.com/zaidbharde/FORESIGHT.git
cd FORESIGHT

# Install Python dependencies
cd backend
pip install -r requirements.txt

# Start the API
uvicorn backend.main:app --reload
```

### Frontend Setup

```bash
cd frontend
npm install
npm run dev

---

## 📡 API Reference

### `POST /predict`

Accepts transaction features and returns risk score with SHAP explanations.

**Request**
```json
{
  "amount": 15000,
  "is_new_beneficiary": true,
  "hour_of_day": 2,
  "is_round_amount": true,
  "call_active": false,
  "device_changed": false,
  "transactions_last_hour": 1
}
```

**Response**
```json
{
  "risk_score": 74,
  "risk_category": "HIGH",
  "recommended_action": "OTP_VERIFICATION",
  "shap_reasons": [
    "New beneficiary contributed 34% to risk score",
    "Late night transaction contributed 28% to risk score",
    "Amount 3x above your average contributed 22% to risk score"
  ],
  "otp_fraud_flag": false,
  "processing_time_ms": 12
}
```

### `GET /health`
```json
{ "status": "ok", "model_loaded": true }
```

### `GET /demo-scenarios`
Returns 4 preset scenarios for hackathon demo mode.

### `POST /report-fraud`
Logs a fraud report for a given transaction ID.

---

## 🎭 Demo Scenarios

The app includes one-tap demo scenarios for live presentation:

| Scenario | Risk Level | What Triggers |
|----------|-----------|---------------|
| 👨‍👩‍👧 Pay family member ₹5,000 | 🟢 LOW | Trusted Circle — instant |
| 🏪 New vendor ₹18,000 | 🟡 MEDIUM | Secure Mode + hold-to-confirm |
| 📞 OTP scam simulation | 🔴 HIGH | Call active + pressure detected |
| 🚨 Critical freeze | ⛔ CRITICAL | Score 80+ — kill switch shown |

---

## 📊 Model Performance

| Metric | Random Forest | XGBoost |
|--------|--------------|---------|
| Accuracy | 98.2% | 98.7% |
| Precision (Fraud) | 91.4% | 93.1% |
| Recall (Fraud) | 87.3% | 90.6% |
| F1-Score | 89.3% | 91.8% |
| ROC-AUC | 0.967 | 0.981 |

> **Primary metric is Recall** — missing a fraud is worse than a false alarm.

---

## 🎯 Why FORESIGHT Beats the RBI Approach

| Feature | RBI 1-Hour Delay | FORESIGHT |
|---------|-----------------|-----------|
| Emergency payments | ❌ Blocked 1 hour | ✅ Instant if trusted |
| Fraud prevention | ⚠️ Passive delay | 🛡️ Active ML detection |
| User experience | ❌ Universal friction | ✅ Friction only when needed |
| OTP scam protection | ❌ None | ✅ Dedicated shield layer |
| Explainability | ❌ None | ✅ SHAP reasons shown |
| Elderly support | ❌ None | ✅ Voice readout + guardian mode |

---

## 🗺️ Roadmap

- [x] Android app built with Jetpack Compose
- [x] FastAPI backend with hybrid AI Risk Engine
- [x] Firebase Phone Authentication
- [x] UPI payment flow and QR payment support
- [x] QR code scanner integration
- [x] AI-powered real-time fraud risk assessment
- [x] Rule-based fraud detection engine
- [x] ML model trained using IEEE-CIS Fraud Detection dataset
- [x] Explainable AI (SHAP) integration
- [x] Hybrid Rule + ML risk scoring
- [x] Transaction history
- [x] Multi-language support (English & Hindi)
- [x] Light/Dark/System theme support
- [x] Safety Hub
- [x] Trusted Contacts
- [x] Security hardening and penetration testing
- [x] Secure Firebase authentication
- [x] Backend API with Swagger documentation

### 🚧 Planned

- [ ] Production cloud deployment
- [ ] HTTPS + SSL backend hosting
- [ ] Certificate pinning
- [ ] Encrypted local storage
- [ ] Play Store release
- [ ] RBI sandbox / real banking API integration
- [ ] Push notification fraud alerts
- [ ] Admin analytics dashboard
---

## 🛠️ Tech Stack

**Frontend**
- React 18 + Vite
- Tailwind CSS
- Framer Motion
- Lucide React
- React Router DOM

**Backend**
- FastAPI
- XGBoost + Scikit-learn
- SHAP
- Pandas + NumPy
- Pydantic v2
- Uvicorn

**ML Pipeline**
- Dataset: IEEE-CIS Fraud Detection (Kaggle)
- SMOTE for class imbalance
- RandomizedSearchCV for tuning
- Pickle for model serialization

---

## 👨‍💻 Author

**Zaid Bharde**
Building FORESIGHT as a hackathon project — a real policy alternative to RBI's UPI transaction delay proposal.

[![GitHub](https://img.shields.io/badge/GitHub-zaidbharde-181717?style=flat-square&logo=github)](https://github.com/zaidbharde)

---

<div align="center">

**FORESIGHT** — *Think Before You Pay*

🛡️ AI Powered &nbsp;|&nbsp; 🔍 Fraud Protection &nbsp;|&nbsp; ⚡ Safe Payments

</div>
