from gemini_service import ask_gemini


question = "Hãy giải thích Python là gì cho người mới học."

answer = ask_gemini(question)

print(answer)