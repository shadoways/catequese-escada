package auth

import (
	"fmt"
	"time"

	"github.com/golang-jwt/jwt/v5"
)

type Claims struct {
	Email string   `json:"email"`
	Nome  string   `json:"nome"`
	Roles []string `json:"roles"`
	jwt.RegisteredClaims
}

type JWTService struct {
	secret    []byte
	expiresIn time.Duration
}

func NewJWTService(secret string, expiresIn time.Duration) (*JWTService, error) {
	if len(secret) < 64 {
		return nil, fmt.Errorf("JWT_SECRET deve ter pelo menos 64 caracteres")
	}
	return &JWTService{
		secret:    []byte(secret),
		expiresIn: expiresIn,
	}, nil
}

func (s *JWTService) GenerateAccessToken(email, nome string, roles []string) (string, error) {
	now := time.Now()
	claims := Claims{
		Email: email,
		Nome:  nome,
		Roles: roles,
		RegisteredClaims: jwt.RegisteredClaims{
			Subject:   email,
			IssuedAt:  jwt.NewNumericDate(now),
			ExpiresAt: jwt.NewNumericDate(now.Add(s.expiresIn)),
		},
	}

	token := jwt.NewWithClaims(jwt.SigningMethodHS512, claims)
	return token.SignedString(s.secret)
}

func (s *JWTService) ValidateToken(raw string) (bool, *Claims) {
	claims := &Claims{}
	token, err := jwt.ParseWithClaims(raw, claims, func(token *jwt.Token) (interface{}, error) {
		if token.Method != jwt.SigningMethodHS512 {
			return nil, fmt.Errorf("unexpected signing method")
		}
		return s.secret, nil
	})
	if err != nil || token == nil || !token.Valid {
		return false, nil
	}
	return true, claims
}
