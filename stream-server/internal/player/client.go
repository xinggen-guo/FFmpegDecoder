package player

import (
	"fmt"
	"io"
	"net"
)

// Connect returns a net.Conn as an io.ReadCloser to read FLV data.
func Connect(host string, port int) (io.ReadCloser, error) {
	addr := fmt.Sprintf("%s:%d", host, port)
	conn, err := net.Dial("tcp", addr)
	if err != nil {
		return nil, err
	}
	return conn, nil
}
