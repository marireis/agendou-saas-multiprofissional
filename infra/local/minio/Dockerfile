FROM golang:1.25-bookworm AS build
ARG MINIO_COMMIT=7aac2a2c5b7c882e68c1ce017d8256be2feea27f
RUN CGO_ENABLED=0 GOMAXPROCS=2 go install -p 2 github.com/minio/minio@${MINIO_COMMIT}

FROM debian:bookworm-slim
COPY --from=build /go/bin/minio /usr/local/bin/minio
COPY --from=build /etc/ssl/certs/ca-certificates.crt /etc/ssl/certs/ca-certificates.crt
COPY --from=build /go/pkg/mod/github.com/minio/minio@*/LICENSE /usr/share/licenses/minio/LICENSE
EXPOSE 9000 9001
ENTRYPOINT ["minio"]
CMD ["server", "/data", "--console-address", ":9001"]
