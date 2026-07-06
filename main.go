package main

import (
	"embed"
	"fmt"
	"io"
	"io/fs"
	"net"
	"net/http"
	"os"
	"os/signal"
	"syscall"

	"golang.design/x/clipboard"
	qrcode "github.com/skip2/go-qrcode"
)

//go:embed public
var content embed.FS

func getLocalIP() string {
	addrs, err := net.InterfaceAddrs()
	if err != nil {
		return "127.0.0.1"
	}
	for _, addr := range addrs {
		if ipnet, ok := addr.(*net.IPNet); ok && !ipnet.IP.IsLoopback() && ipnet.IP.To4() != nil {
			return ipnet.IP.String()
		}
	}
	return "127.0.0.1"
}

func main() {
	err := clipboard.Init()
	if err != nil {
		fmt.Println("Error iniciando clipboard:", err)
		os.Exit(1)
	}

	port := "3737"
	ip := getLocalIP()
	url := fmt.Sprintf("http://%s:%s", ip, port)

	fmt.Println("Escaneá este QR desde tu celular (misma red WiFi):")
	qr, _ := qrcode.New(url, qrcode.Medium)
	fmt.Print(qr.ToSmallString(false))
	fmt.Printf("\nO abrí: %s\n", url)

	http.HandleFunc("/clipboard", func(w http.ResponseWriter, r *http.Request) {
		switch r.Method {
		case http.MethodGet:
			text := clipboard.Read(clipboard.FmtText)
			io.WriteString(w, string(text))
		case http.MethodPost:
			body, err := io.ReadAll(r.Body)
			if err == nil {
				clipboard.Write(clipboard.FmtText, body)
				io.WriteString(w, "ok")
			} else {
				http.Error(w, "error", http.StatusInternalServerError)
			}
		}
	})

	sub, _ := fs.Sub(content, "public")
	http.Handle("/", http.FileServer(http.FS(sub)))

	go func() {
		sig := make(chan os.Signal, 1)
		signal.Notify(sig, syscall.SIGINT, syscall.SIGTERM)
		<-sig
		fmt.Println("\nCerrando servidor...")
		os.Exit(0)
	}()

	fmt.Printf("Servidor corriendo en %s\n", url)
	http.ListenAndServe("0.0.0.0:"+port, nil)
}
