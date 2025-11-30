package config

import (
	"log"
	"os"

	"gopkg.in/yaml.v3"
)

type ServerConfig struct {
	Host string `yaml:"host"`
	Port int    `yaml:"port"`
}

type TCPConfig struct {
	ReadTimeoutMS  int `yaml:"read_timeout_ms"`
	WriteTimeoutMS int `yaml:"write_timeout_ms"`
}

type FLVConfig struct {
	WriteBuffer int `yaml:"write_buffer"`
}

type LogConfig struct {
	Level string `yaml:"level"`
}

type Config struct {
	Server ServerConfig `yaml:"server"`
	TCP    TCPConfig    `yaml:"tcp"`
	FLV    FLVConfig    `yaml:"flv"`
	Log    LogConfig    `yaml:"log"`
}

// Load reads YAML config from path.
// It will log.Fatal on unrecoverable errors for simplicity.
func Load(path string) *Config {
	f, err := os.Open(path)
	if err != nil {
		log.Fatalf("open config: %v", err)
	}
	defer f.Close()

	var cfg Config
	dec := yaml.NewDecoder(f)
	if err := dec.Decode(&cfg); err != nil {
		log.Fatalf("decode config: %v", err)
	}

	return &cfg
}
