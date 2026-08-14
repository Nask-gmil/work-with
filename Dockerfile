# --- ビルド用ステージ ---
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# 依存関係の定義だけ先にコピーしてキャッシュを効かせる
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x ./mvnw

# ソースコードとフロントエンド資材をコピーしてビルド
# （pom.xmlのresources設定で、これらがstaticフォルダへコピーされます）
COPY src ./src
COPY main.html ./main.html
COPY css ./css
COPY js ./js
COPY work-space-pic ./work-space-pic
RUN ./mvnw clean package -DskipTests

# --- 実行用ステージ ---
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /app/target/work-with-0.0.1-SNAPSHOT.jar app.jar

# Renderが割り当てるPORT環境変数をSpring Bootに渡す
ENV PORT=8080
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
