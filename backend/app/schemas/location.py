from pydantic import BaseModel, ConfigDict


class LocationOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: str
    type: str
    code: str
    name_en: str
    name_bn: str
    city_en: str
    city_bn: str
    country: str
    is_popular: bool
