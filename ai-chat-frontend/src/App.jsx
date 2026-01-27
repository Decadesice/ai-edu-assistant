import React from "react";
import { Navigate, Route, Routes } from "react-router-dom";
import Layout from "./components/Layout.jsx";
import Login from "./pages/Login.jsx";
import Home from "./pages/Home.jsx";
import KnowledgeManagement from "./pages/KnowledgeManagement.jsx";
import IntelligentQA from "./pages/IntelligentQA.jsx";
import QuestionGeneration from "./pages/QuestionGeneration.jsx";
import LearningStatistics from "./pages/LearningStatistics.jsx";

function RequireAuth({ children }) {
  const token = localStorage.getItem("token");
  if (!token) {
    return <Navigate to="/login" replace />;
  }
  return children;
}

function RequireNoAuth({ children }) {
  const token = localStorage.getItem("token");
  if (token) {
    return <Navigate to="/" replace />;
  }
  return children;
}

export default function App() {
  return (
    <Routes>
      <Route
        path="/login"
        element={
          <RequireNoAuth>
            <Login />
          </RequireNoAuth>
        }
      />
      <Route
        path="/"
        element={
          <RequireAuth>
            <Layout />
          </RequireAuth>
        }
      >
        <Route index element={<Home />} />
        <Route path="knowledge" element={<KnowledgeManagement />} />
        <Route path="qa" element={<IntelligentQA />} />
        <Route path="generate" element={<QuestionGeneration />} />
        <Route path="stats" element={<LearningStatistics />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
