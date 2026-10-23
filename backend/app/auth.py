"""Autenticación por token Bearer estático (un solo usuario)."""
from fastapi import Header, HTTPException, status

from .config import settings


def verify_token(authorization: str = Header(default="")):
    """Valida el header 'Authorization: Bearer <APP_TOKEN>'."""
    expected = f"Bearer {settings.app_token}"
    if not authorization or authorization != expected:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Token inválido o ausente",
            headers={"WWW-Authenticate": "Bearer"},
        )
    return True
