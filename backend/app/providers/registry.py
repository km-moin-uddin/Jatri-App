"""Finds the provider adapter that owns a booking (by `Booking.provider`), so holds can be
released and paid bookings confirmed whatever the transport mode."""

from typing import Annotated

from fastapi import Depends

from app.providers.base import ProviderError
from app.providers.mock_bus import MockBusProvider, get_bus_provider
from app.providers.mock_flight import MockFlightProvider, get_flight_provider
from app.providers.mock_train import MockTrainProvider, get_train_provider


class Providers:
    def __init__(self, bus: MockBusProvider, flight: MockFlightProvider, train: MockTrainProvider | None = None) -> None:
        self.bus = bus
        self.flight = flight
        self.train = train or get_train_provider()
        self._by_name = {bus.name: bus, flight.name: flight, self.train.name: self.train}

    def get(self, name: str):
        provider = self._by_name.get(name)
        if provider is None:
            raise ProviderError("provider_unavailable", "This provider is not available.", "এই সেবাদাতা এখন পাওয়া যাচ্ছে না।")
        return provider


def get_providers(
    bus: Annotated[MockBusProvider, Depends(get_bus_provider)],
    flight: Annotated[MockFlightProvider, Depends(get_flight_provider)],
    train: Annotated[MockTrainProvider, Depends(get_train_provider)],
) -> Providers:
    return Providers(bus, flight, train)


def default_providers() -> Providers:
    """For code outside a request (the background worker)."""
    return Providers(get_bus_provider(), get_flight_provider(), get_train_provider())


ProvidersDep = Annotated[Providers, Depends(get_providers)]
