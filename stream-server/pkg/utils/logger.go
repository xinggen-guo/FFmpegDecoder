package utils

import (
	"log"
	"os"
)

// NewStdLogger returns a basic logger with prefix.
func NewStdLogger(prefix string) *log.Logger {
	return log.New(os.Stdout, prefix, log.LstdFlags|log.Lshortfile)
}
